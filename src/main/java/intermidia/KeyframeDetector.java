package intermidia;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;

import org.openimaj.feature.DoubleFVComparison;
import org.openimaj.image.ImageUtilities;
import org.openimaj.image.pixel.statistics.HistogramModel;
import org.openimaj.math.statistics.distribution.MultidimensionalHistogram;
import org.openimaj.video.xuggle.XuggleVideo;

import TVSSUnits.Shot;
import TVSSUnits.ShotList;
import TVSSUtils.ShotReader;

public class KeyframeDetector 
{
	private static class FrameInfo implements Comparable<FrameInfo>
	{
		private int index;
		private MultidimensionalHistogram histogram;
		private double meanDistance;
		
		public int getIndex()
		{
			return this.index;
		}
		
		public MultidimensionalHistogram getHistogram()
		{
			return this.histogram;
		}
		
		public FrameInfo(int index, MultidimensionalHistogram histogram)
		{
			this.index = index;
			this.histogram = histogram;
		}
		
		public void setMeanDistance(double distance)
		{
			this.meanDistance = distance;		
		}
		
		public double getMeanDistance()
		{
			return this.meanDistance;
		}

		public int compareTo(FrameInfo otherFrameInfo) 
		{
			if(this.index == otherFrameInfo.index)
			{
				return 0;
			}
			else
			{
				if(this.index < otherFrameInfo.index)
				{
					return -1;
				}
				else
				{
					return 1;
				}
				
			}
		}
		
	}
	private static class FrameInfoComparator implements Comparator<FrameInfo>
	{
		public int compare(FrameInfo firstFrameInfo, FrameInfo secondFrameInfo) 
		{			
			return firstFrameInfo.compareTo(secondFrameInfo);
		}	 
	}
	
		
	/*Usage: <in: video> <in: shot annotation> <out: keyframe annotation> <out: keyframe images> <in: similarity threshold> */
    public static void main( String[] args ) throws Exception
    {
    	double stopThreshold = Float.parseFloat(args[4]);
    		
		XuggleVideo source = new XuggleVideo(new File(args[0]));
		XuggleVideo auxSource = new XuggleVideo(new File(args[0]));
		//For some reason first two getNextFrame() returns 0.
		source.getNextFrame();		
		ShotList shotList = ShotReader.readFromCSV(args[1]);
		
		
		//System.out.println("Reading video.");		
		FileWriter keyframeWriter = new FileWriter(args[2]);
		int shotIndex = 0;
		///////////////////////////////////////FRAMEDEBUG START
/*		for(Shot shot: shotList.getList())	
		{
			int firstFrame = (int)shot.getStartBoundary();
			int lastFrame = (int)shot.getEndBoundary();
			ArrayList<FrameInfo> shotFrames = new ArrayList<FrameInfo>();
			ArrayList<FrameInfo> shotKeyframes = new ArrayList<FrameInfo>();
			
			//Advance video pointer to the shot beginning
			while(source.getCurrentFrameIndex() < firstFrame && source.hasNextFrame())
			{
				source.getNextFrame();
			}
			ImageUtilities.write(source.getCurrentFrame(), new File("boundaries/"+String.format("%06d", source.getCurrentFrameIndex()) + ".jpg"));
			
			//Advance video pointer to the shot end
			while(source.getCurrentFrameIndex() < lastFrame && source.hasNextFrame())
			{
				source.getNextFrame();
			}
			ImageUtilities.write(source.getCurrentFrame(), new File("boundaries/"+String.format("%06d", source.getCurrentFrameIndex()) + ".jpg"));
			
		}
		System.exit(0);*/
		///////////////////////////////////////FRAMEDEBUG END		
		for(Shot shot: shotList.getList())	
		{	
			int firstFrame = (int)shot.getStartBoundary();
			int lastFrame = (int)shot.getEndBoundary();
			ArrayList<FrameInfo> shotFrames = new ArrayList<FrameInfo>();
			ArrayList<FrameInfo> shotKeyframes = new ArrayList<FrameInfo>();
			
			//Advance video pointer to the shot beginning
			while(source.getCurrentFrameIndex() < firstFrame && source.hasNextFrame())
			{
				source.getNextFrame();
			}
			//Calculate a color histogram of each frame in the shot
			int histIndex = 0;		
			HistogramModel histogramModel = new HistogramModel(4,4,4);		
			
			while(source.getCurrentFrameIndex() <= lastFrame && 
					source.hasNextFrame() &&
					histIndex < (lastFrame - firstFrame + 1)
				 )
			{
				histogramModel.estimateModel(source.getCurrentFrame());
				shotFrames.add(new FrameInfo(source.getCurrentFrameIndex(), histogramModel.histogram.clone()));				
				source.getNextFrame();
			}
			
			//Calculate mean distance from a frame to all other in same shot
			for(int i = 0; i < shotFrames.size(); i++)
			{
				double meanDistance = 0;
				for(int j = 0; j < shotFrames.size(); j++)
				{
					// 1- similarity to obtain distance
					meanDistance += (1 - shotFrames.get(i).getHistogram().compare(shotFrames.get(j).getHistogram(), DoubleFVComparison.INTERSECTION));
				}
				meanDistance /= shotFrames.size();
				shotFrames.get(i).setMeanDistance(meanDistance);
			}
			
			
			//Find first keyframe, one which is closest to all others in the shot.
			FrameInfo lessDistantFrame = shotFrames.remove(0);
			for(FrameInfo candidateFrame : shotFrames)
			{
				if(lessDistantFrame.getMeanDistance() > candidateFrame.getMeanDistance())
				{
					FrameInfo aux = lessDistantFrame;
					lessDistantFrame = candidateFrame;
					candidateFrame = aux;
				}
			}
			shotKeyframes.add(lessDistantFrame);									
			
			//Now find next keyframes, ones which are farthest from the all others in the keyframe list until a threshold is reached			
			double meanDistance = stopThreshold;
			while(!shotFrames.isEmpty() && meanDistance >= stopThreshold)
			{
				for(FrameInfo candidateFrame : shotFrames)
				{
					meanDistance = 0;
					for(FrameInfo keyFrame : shotKeyframes)
					{
						// 1- similarity to obtain distance
						meanDistance += (1 - candidateFrame.getHistogram().compare(keyFrame.getHistogram(), DoubleFVComparison.INTERSECTION));					
					}
					meanDistance /= shotKeyframes.size();
					candidateFrame.setMeanDistance(meanDistance);
				}	
				//System.out.println("Mean dist: " + meanDistance);
			
				if(meanDistance >= stopThreshold)
				{
					FrameInfo moreDistantFrame = shotFrames.remove(0);
					for(FrameInfo candidateFrame : shotFrames)				
					{
						if(candidateFrame.getMeanDistance() > moreDistantFrame.getMeanDistance())
						{
							FrameInfo aux = moreDistantFrame;
							moreDistantFrame = candidateFrame;
							candidateFrame = aux;
						}
					}
					shotKeyframes.add(moreDistantFrame);
				}
			}
			
			int kfNum = 0;
			shotKeyframes.sort(new FrameInfoComparator());
			for(FrameInfo keyframe : shotKeyframes)
			{
				keyframeWriter.write(shotIndex + "\t" + keyframe.getIndex() + "\n");
				/*Create image file*/
				String folder = args[3];
				String keyframeName = "s" + String.format("%04d", shotIndex) + "kf" + String.format("%04d", kfNum++) + ".jpg";
				while(auxSource.getCurrentFrameIndex() < keyframe.getIndex())
				{
					auxSource.getNextFrame();
				}
				//VideoPinpointer.seek(auxSource, keyframe.getIndex());
				
				ImageUtilities.write(auxSource.getCurrentFrame(), new File(folder + keyframeName));
				/*System.out.println("Shot " + shotIndex + ": " + shot.getStartBoundary() + " - " +  shot.getEndBoundary() +
						" | Keyframe @ " + auxSource.getCurrentFrameIndex());*/
			}
			shotIndex++;
		}		
		source.close();
		auxSource.close();
		keyframeWriter.close();
		//System.exit(0); //Exit Success
    }
}


 