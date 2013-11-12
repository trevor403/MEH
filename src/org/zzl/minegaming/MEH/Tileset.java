package org.zzl.minegaming.MEH;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;

import org.zzl.minegaming.GBAUtils.BitConverter;
import org.zzl.minegaming.GBAUtils.GBAImage;
import org.zzl.minegaming.GBAUtils.GBAImageType;
import org.zzl.minegaming.GBAUtils.GBARom;
import org.zzl.minegaming.GBAUtils.Lz77;
import org.zzl.minegaming.GBAUtils.Palette;
import org.zzl.minegaming.GBAUtils.ROMManager;

public class Tileset
{
	private GBARom rom;
	
	private GBAImage image;
	private BufferedImage[] bi;
	private Palette[] palettes; //Main gets 7, local gets 5
	private static Tileset lastPrimary;
	private boolean isPrimary = true;
    public TilesetHeader tilesetHeader;

	public final int numBlocks;
	private HashMap<Integer,BufferedImage>[] renderedTiles;
	private HashMap<Integer,BufferedImage>[] customRenderedTiles;
	private final byte[] localTSLZHeader = new byte[] { 10, 80, 9, 00, 32, 00, 00 };
	private final byte[] globalTSLZHeader = new byte[] { 10, 80, 9, 00, 32, 00, 00 };


	@SuppressWarnings("unchecked")
	public Tileset(GBARom rom, int offset)
	{
		this.rom = rom;
		tilesetHeader=new TilesetHeader(rom, offset);
		

		
		if(tilesetHeader.isPrimary)
			lastPrimary = this;
		int[] uncompressedData = null;
		

		if(tilesetHeader.bCompressed == 1)
			uncompressedData = Lz77.decompressLZ77(rom, (int)tilesetHeader.pGFX);
		if(uncompressedData == null)
		{
			GBARom backup = (GBARom) rom.clone(); //Backup in case repairs fail
			rom.writeBytes(offset, (isPrimary ? globalTSLZHeader : localTSLZHeader)); //Attempt to repair the LZ77 data
			uncompressedData = Lz77.decompressLZ77(rom, (int)tilesetHeader.pGFX);
			rom = (GBARom) backup.clone(); //TODO add dialog to allow repairs to be permanant
			if(uncompressedData == null) //If repairs didn't go well, revert ROM and pull uncompressed data
			{
				uncompressedData = BitConverter.ToInts(rom.readBytes((int)tilesetHeader.pGFX, (isPrimary ? 128*DataStore.MainTSHeight : 128*DataStore.LocalTSHeight) / 2)); //TODO: Hardcoded to FR tileset sizes
			}
		}
		numBlocks = (isPrimary ? DataStore.MainTSBlocks : DataStore.LocalTSBlocks); //INI RSE=0x207 : 0x88, FR=0x280 : 0x56
		renderedTiles = (HashMap<Integer,BufferedImage>[])new HashMap[isPrimary ? DataStore.MainTSPalCount : 13];
		customRenderedTiles = (HashMap<Integer,BufferedImage>[])new HashMap[13-DataStore.MainTSPalCount];
		
		for(int i = 0; i < (isPrimary ? DataStore.MainTSPalCount : 13); i++)
			renderedTiles[i] = new HashMap<Integer,BufferedImage>();
		for(int i = 0; i < 13-DataStore.MainTSPalCount; i++)
			customRenderedTiles[i] = new HashMap<Integer,BufferedImage>();

		palettes = new Palette[13];
		bi = new BufferedImage[13];
		
		for(int i = 0; i < (isPrimary ? DataStore.MainTSPalCount : 13); i++)
		{
			palettes[i] = new Palette(GBAImageType.c16, rom.readBytes((int)tilesetHeader.pPalettes+(32*i),32));
		}
		
		
		image = new GBAImage(uncompressedData,palettes[0],new Point(128,(isPrimary ? DataStore.MainTSHeight : DataStore.LocalTSHeight)));	
	}
	
	public void startTileThreads()
	{
		for(int i = 0; i < (isPrimary ? DataStore.MainTSPalCount : 13); i++)
			new TileLoader(renderedTiles,i).start();
	}
	
	public BufferedImage getTileWithCustomPal(int tileNum, Palette palette, boolean xFlip, boolean yFlip)
	{
		int x = ((tileNum) % (bi[0].getWidth() / 8)) * 8;
		int y = ((tileNum) / (bi[0].getWidth() / 8)) * 8;
		BufferedImage toSend =  image.getBufferedImageFromPal(palette).getSubimage(x, y, 8, 8);

		if(!xFlip && !yFlip)
			return toSend;
		if(xFlip)
			toSend = horizontalFlip(toSend);
		if(yFlip)
			toSend = verticalFlip(toSend);
		
		return toSend;
	}

	public BufferedImage getTile(int tileNum, int palette, boolean xFlip, boolean yFlip)
	{
		if(palette < DataStore.MainTSPalCount)
		{
		if(renderedTiles[palette].containsKey(tileNum)) //Check to see if we've cached that tile
		{
			if(xFlip && yFlip)
				return verticalFlip(horizontalFlip(renderedTiles[palette].get(tileNum)));
			else if(xFlip)
			{
				return horizontalFlip(renderedTiles[palette].get(tileNum));
			}
			else if(yFlip)
			{
				return verticalFlip(renderedTiles[palette].get(tileNum));
			}
			
			return renderedTiles[palette].get(tileNum);
		}
		}
		else if(palette < 13)
		{
			if(customRenderedTiles[palette-DataStore.MainTSPalCount].containsKey(tileNum)) //Check to see if we've cached that tile
			{
				if(xFlip && yFlip)
					return verticalFlip(horizontalFlip(customRenderedTiles[palette-DataStore.MainTSPalCount].get(tileNum)));
				else if(xFlip)
				{
					return horizontalFlip(customRenderedTiles[palette-DataStore.MainTSPalCount].get(tileNum));
				}
				else if(yFlip)
				{
					return verticalFlip(customRenderedTiles[palette-DataStore.MainTSPalCount].get(tileNum));
				}
				
				return customRenderedTiles[palette-DataStore.MainTSPalCount].get(tileNum);
			}
		}
		else
		{
			System.out.println("Attempted to read tile " + tileNum + " of palette " + palette + " in " + (isPrimary ? "global" : "local") + " tileset!");
			return new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
		}
		
		int x = ((tileNum) % (128 / 8)) * 8;
		int y = ((tileNum) / (128 / 8)) * 8;
		BufferedImage toSend = new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
		try
		{
			toSend =  bi[palette].getSubimage(x, y, 8, 8);
		}
		catch(Exception e)
		{
			//e.printStackTrace();
			System.out.println("Attempted to read 8x8 at " + x + ", " + y);
		}
		if(palette < DataStore.MainTSPalCount || renderedTiles.length > DataStore.MainTSPalCount)
			renderedTiles[palette].put(tileNum, toSend);
		else
			customRenderedTiles[palette-DataStore.MainTSPalCount].put(tileNum, toSend);

		if(!xFlip && !yFlip)
			return toSend;
		if(xFlip)
			toSend = horizontalFlip(toSend);
		if(yFlip)
			toSend = verticalFlip(toSend);
		
		return toSend;
	}
	
	public Palette[] getPalette()
	{
		return palettes;
	}
	
	public void setPalette(Palette[] pal)
	{
		palettes = pal;
	}
	
	public void rerenderTileSet(int palette)
	{
			bi[palette] = image.getBufferedImageFromPal(palettes[palette]);
	}
	
	public void renderPalettedTiles()
	{		
		for(int i = 0; i < 13; i++)
		{
			bi[i] = image.getBufferedImageFromPal(palettes[i]);

		}
		for(int i = 0; i < 13; i++)
			rerenderTileSet(i);
	}
	public void resetCustomTiles()
	{
		customRenderedTiles = (HashMap<Integer,BufferedImage>[])new HashMap[DataStore.MainTSPalCount];
		for(int i = 0; i < DataStore.MainTSPalCount; i++)
			customRenderedTiles[i] = new HashMap<Integer,BufferedImage>();
	}
	
    private BufferedImage horizontalFlip(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage dimg = new BufferedImage(w, h, img.getType());
        Graphics2D g = dimg.createGraphics();
        g.drawImage(img, 0, 0, w, h, w, 0, 0, h, null);
        g.dispose();
        return dimg;
    }
 
    private BufferedImage verticalFlip(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage dimg = new BufferedImage(w, h, img.getColorModel()
                .getTransparency());
        Graphics2D g = dimg.createGraphics();
        g.drawImage(img, 0, 0, w, h, 0, h, w, 0, null);
        g.dispose();
        return dimg;
    }

	public BufferedImage getTileSet(int palette)
	{
		return bi[palette];
	}

	public int getBlockPointer()
	{
		return (int)(tilesetHeader.pBlocks);
	}

	public int getAnimationPointer()
	{
		return (int)tilesetHeader.pAnimation;
	}

	public GBARom getROM()
	{
		return rom;
	}
	
	private class TileLoader extends Thread implements Runnable
	{
		HashMap<Integer,BufferedImage>[] buffer;
		int pal;
		public TileLoader(HashMap<Integer,BufferedImage>[] hash, int palette)
		{
			buffer = hash;
			pal = palette;
		}
		
		@Override
		public void run()
		{
			int k = (isPrimary ? DataStore.MainTSSize : DataStore.LocalTSSize);
			for(int i = 0; i < numBlocks; i++)
			{
					try
					{
						buffer[pal].put(i, getTile(i,pal,false,false));
					}
					catch(Exception e)
					{
						//e.printStackTrace();
						System.out.println("An error occured while writing tile " + i + " with palette " + pal);
					}
			}
		}
		
	}
}
