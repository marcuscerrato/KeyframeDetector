package intermidia;

import java.io.File;
import java.io.FileWriter;

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
	/*Usage: <in: video> <in: shot annotation> <out: keyframe annotation> <out: keyframe images>*/
    public static void main( String[] args ) throws Exception
    {
		XuggleVideo source = new XuggleVideo(new File(args[0]));
		//For some reason first two getNextFrame() returns 0.
		source.getNextFrame();
		ShotList shotList = ShotReader.readFromCSV(args[1]);					
		
		//System.out.println("Reading video.");		
		FileWriter keyframeWriter = new FileWriter(args[2]);
		int shotIndex = 0;
		for(Shot shot: shotList.getList())	
		{	
			int firstFrame = (int)shot.getStartBoundary();
			int lastFrame = (int)shot.getEndBoundary();
			long frameQty = (lastFrame - firstFrame + 1);						
			//Advance video pointer to the shot beginning
			while(source.getCurrentFrameIndex() < firstFrame && source.hasNextFrame())
			{
				source.getNextFrame();
			}
			//Calculate a color histogram of each frame in the shot
			int histIndex = 0;
			MultidimensionalHistogram histogram[] = new MultidimensionalHistogram[(int) frameQty];			
			HistogramModel histogramModel = new HistogramModel(4,4,4);		
			
			while(source.getCurrentFrameIndex() <= lastFrame && 
					source.hasNextFrame() &&
					histIndex < frameQty
				 )
			{
				histogramModel.estimateModel(source.getCurrentFrame());
				histogram[histIndex++] = histogramModel.histogram.clone();
				source.getNextFrame();
			}
			//Sometimes there are less frames than the expected, this line updates it
			frameQty = histIndex;
			
			//Calculate mean distance from a frame to all other in same shot
			double meanDistance[] = new double[(int) frameQty];
			for(int i = 0; i < frameQty; i++)
			{
				meanDistance[i] = 0;
				for(int j = 0; j < frameQty; j++)
				{
					meanDistance[i] += histogram[i].compare(histogram[j], DoubleFVComparison.EUCLIDEAN);
				}
				meanDistance[i] /= frameQty;
			}
			
			int minDistanceIndex = 0;
			double minDistance = meanDistance[minDistanceIndex];			
			for(int i = 1; i < frameQty; i++)
			{
				if(meanDistance[i] < minDistance)
				{
					minDistanceIndex = i;
					minDistance = meanDistance[minDistanceIndex];
				}
			}
			keyframeWriter.write(shotIndex + "\t" + (firstFrame + minDistanceIndex) + "\n");
			/*Create image file*/
			String folder = args[3];
			int kfNum = 0;
			String keyframeName = "s" + String.format("%04d", shotIndex) + "kf" + String.format("%04d", kfNum) + ".jpg";			
			ImageUtilities.write(source.getCurrentFrame(), new File(folder + keyframeName));
			//System.out.println("Shot " + shotIndex + ": " + shot.getStartBoundary() + " - " +  shot.getEndBoundary() +
			//		" | Keyframe @ " + (firstFrame + minDistanceIndex));
			shotIndex++;
		}		
		source.close();
		keyframeWriter.close();
		System.exit(0); //Exit Success
    }
}
 