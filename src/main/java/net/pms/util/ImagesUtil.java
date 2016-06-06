package net.pms.util;

import java.io.*;

import mediautil.gen.Log;
import mediautil.image.jpeg.LLJTran;
import mediautil.image.jpeg.LLJTranException;
import net.pms.dlna.DLNAMediaInfo;
import net.pms.io.OutputParams;

import org.apache.sanselan.ImageInfo;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImagesUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImagesUtil.class);

	public static InputStream getAutoRotateInputStreamImage(InputStream input, int exifOrientation) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			auto(input, baos, exifOrientation);
		} catch (IOException | LLJTranException e) {
			LOGGER.error("Error in auto rotate", e);
			return null;
		}
		return new ByteArrayInputStream(baos.toByteArray());
	}

	public static void auto(InputStream input, OutputStream output, int exifOrientation) throws IOException, LLJTranException {
		// convert sanselan exif orientation -> llj operation
		int op;
		switch (exifOrientation) {
			case 1:
				op = 0;
				break;
			case 2:
				op = 1;
				break;
			case 3:
				op = 6;
				break;
			case 4:
				op = 2;
				break;
			case 5:
				op = 3;
				break;
			case 6:
				op = 5;
				break;
			case 7:
				op = 4;
				break;
			case 8:
				op = 7;
				break;
			default:
				op = 0;
		}

		// Raise the Debug Level which is normally LEVEL_INFO. Only Warning
		// messages will be printed by MediaUtil.
		Log.debugLevel = Log.LEVEL_NONE;

		// 1. Initialize LLJTran and Read the entire Image including Appx markers
		LLJTran llj = new LLJTran(input);
		// If you pass the 2nd parameter as false, Exif information is not
		// loaded and hence will not be written.
		llj.read(LLJTran.READ_ALL, true);

		// 2. Transform the image using default options along with
		// transformation of the Orientation tags. Try other combinations of
		// LLJTran_XFORM.. flags. Use a jpeg with partial MCU (partialMCU.jpg)
		// for testing LLJTran.XFORM_TRIM and LLJTran.XFORM_ADJUST_EDGES
		int options = LLJTran.OPT_DEFAULTS | LLJTran.OPT_XFORM_ORIENTATION;
		llj.transform(op, options);

		// 3. Save the Image which is already transformed as specified by the
		//    input transformation in Step 2, along with the Exif header.
		try (OutputStream out = new BufferedOutputStream(output)) {
			llj.save(out, LLJTran.OPT_WRITE_ALL);
		}

		// Cleanup
		input.close();
		llj.freeMemory();
	}

	/**
	 * This method populates the supplied {@link DLNAMediaInfo} object with some EXIF data 
	 * (MODEL, EXPOSURE TIME, ORIENTATION, ISO).
	 *
	 * @param file
	 * The image file to be parsed
	 * @param media
	 * The MediaInfo metadata which will be populated
	 */
	public static void parseImageMetadata (File file, DLNAMediaInfo media) {
		try {
			IImageMetadata meta = Sanselan.getMetadata(file);
			if (meta != null && meta instanceof JpegImageMetadata) {
				JpegImageMetadata jpegmeta = (JpegImageMetadata) meta;
				TiffField tf = jpegmeta.findEXIFValue(TiffConstants.EXIF_TAG_MODEL);
				if (tf != null) {
					media.setModel(tf.getStringValue().trim());
				}

				tf = jpegmeta.findEXIFValue(TiffConstants.EXIF_TAG_EXPOSURE_TIME);
				if (tf != null) {
					media.setExposure((int) (1000 * tf.getDoubleValue()));
				}

				tf = jpegmeta.findEXIFValue(TiffConstants.EXIF_TAG_ORIENTATION);
				if (tf != null) {
					media.setOrientation(tf.getIntValue());
				}

				tf = jpegmeta.findEXIFValue(TiffConstants.EXIF_TAG_ISO);
				if (tf != null) {
					// Galaxy Nexus jpg pictures may contain multiple values, take the first
					int[] isoValues = tf.getIntArrayValue();
					media.setIso(isoValues[0]);
				}
			}
		} catch (ImageReadException | IOException e) {
			LOGGER.info("Error parsing EXIF metadata of image ({}).", file.getAbsolutePath());
		}
	}
}
