package forge.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.badlogic.gdx.files.FileHandle;

import forge.Forge;
import forge.gui.GuiBase;
import forge.localinstance.properties.ForgeConstants;

public class LibGDXImageFetcher extends ImageFetcher {
    @Override
    protected Runnable getDownloadTask(String[] downloadUrls, String destPath, Runnable notifyObservers) {
        return new LibGDXDownloadTask(downloadUrls, destPath, notifyObservers);
    }

    private static class LibGDXDownloadTask implements Runnable {
        private final String[] downloadUrls;
        private final String destPath;
        private final Runnable notifyObservers;

        LibGDXDownloadTask(String[] downloadUrls, String destPath, Runnable notifyObservers) {
            this.downloadUrls = downloadUrls;
            this.destPath = destPath;
            this.notifyObservers = notifyObservers;
        }

        private void doFetch(String urlToDownload) throws IOException {
            String newdespath = urlToDownload.contains(".fullborder.") || urlToDownload.startsWith(ForgeConstants.URL_PIC_SCRYFALL_DOWNLOAD) ?
                    TextUtil.fastReplace(destPath, ".full.", ".fullborder.") : destPath;
            URL url = new URL(urlToDownload);
            System.out.println("Attempting to fetch: " + url);
            java.net.URLConnection c = url.openConnection();
            c.setRequestProperty("User-Agent", "");

            InputStream is = c.getInputStream();
            // First, save to a temporary file so that nothing tries to read
            // a partial download.
            FileHandle destFile = new FileHandle(newdespath + ".tmp");
            System.out.println(newdespath);
            destFile.parent().mkdirs();
            OutputStream out = new FileOutputStream(destFile.file());
            // Conversion to JPEG will be handled differently depending on the platform
            Forge.getDeviceAdapter().convertToJPEG(is, out);
            is.close();
            out.close(); //close outputstream before destfile.moveto so it can delete the tmp file internally
            destFile.moveTo(new FileHandle(newdespath));

            System.out.println("Saved image to " + newdespath);
            GuiBase.getInterface().invokeInEdtLater(notifyObservers);
        }

        private String tofullBorder(String imageurl) {
            if (!imageurl.contains(".full.jpg"))
                return imageurl;
            try {
                URL url = new URL(imageurl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                //connection.setConnectTimeout(1000 * 5); //wait 5 seconds the most
                //connection.setReadTimeout(1000 * 5);
                conn.setRequestProperty("User-Agent", "");
                if(conn.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
                    imageurl = TextUtil.fastReplace(imageurl, ".full.jpg", ".fullborder.jpg");
                conn.disconnect();
                return imageurl;
            } catch (IOException ex) {
                return imageurl;
            }
        }

        public void run() {
            for (String urlToDownload : downloadUrls) {
                try {
                    doFetch(tofullBorder(urlToDownload));
                    break;
                } catch (IOException e) {
                    System.out.println("Failed to download card [" + destPath + "] image: " + e.getMessage());
                    if (urlToDownload.contains("tokens")) {
                        int setIndex = urlToDownload.lastIndexOf('_');
                        int typeIndex = urlToDownload.lastIndexOf('.');
                        String setlessFilename = urlToDownload.substring(0, setIndex);
                        String extension = urlToDownload.substring(typeIndex);
                        urlToDownload = setlessFilename+extension;
                        try {
                            doFetch(tofullBorder(urlToDownload));
                            break;
                        } catch (IOException t) {
                            System.out.println("Failed to download setless token [" + destPath + "]: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

}
