package com.mobileapps.bao.stylit;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class NetworkUtils {
    public static final String BASE_URL="https://api.flickr.com/services/rest/?";
    public static final String API_KEY="01a89092533be739f0bdf817eba57046";
    public static String getBaseUrl(String parameter, String sort) {
        Uri base_uri = Uri.parse(BASE_URL).buildUpon()
                .appendQueryParameter("method","flickr.photos.search")
                .appendQueryParameter("api_key", API_KEY)
                .appendQueryParameter("text", parameter)
                .appendQueryParameter("sort", sort)
                .appendQueryParameter("format", "json")
                .appendQueryParameter("nojsoncallback","?")
                .build();
        return base_uri.toString();
    }
    public static String getImageUrl(String id, String secret, String server, int farm)
    {
        String s="https://farm"+farm+".staticflickr.com";
        Uri uri= Uri.parse(s).buildUpon()
                .appendPath(server)
                .appendPath(id+"_"+secret+".jpg")
                .build();
        return uri.toString();
    }
    public static String getTheResponse(URL url) throws IOException {
        HttpURLConnection httpURLConnection=(HttpURLConnection) url.openConnection();
        int statusCode=httpURLConnection.getResponseCode();
        InputStream in;
        if (statusCode >= 200 && statusCode < 400) {
            // Create an InputStream in order to extract the response object
            in = httpURLConnection.getInputStream();
        }
        else {
            in = httpURLConnection.getErrorStream();
        }
        try {
            Scanner scanner = new Scanner(in);
            scanner.useDelimiter("\\A");

            boolean hasInput = scanner.hasNext();
            if (hasInput) {
                return scanner.next();
            } else {
                return null;
            }
        } finally {
            httpURLConnection.disconnect();
        }
    }
}
