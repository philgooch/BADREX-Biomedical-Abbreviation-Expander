/*
 *  Copyright (c) 2012, Phil Gooch.
 *
 *  This software is licenced under the GNU Library General Public License,
 *  http://www.gnu.org/copyleft/gpl.html Version 3, 29 June 2007
 *
 *  Phil Gooch 04/2012
*/

package org.philgooch;

import java.util.*;
import java.io.*;
import java.net.*;

/**
 *
 * @author philipgooch
 */
public class ConfigReader {
    private URL configURL;

    private HashMap<String, String> options;

    public HashMap<String, String> getOptions() {
        return options;
    }

    public URL getConfigURL() {
        return configURL;
    }

    public void setConfigURL(URL configURL) {
        this.configURL = configURL;
    }

    public ConfigReader() {
        this.options = new HashMap<String, String>();
    }

    /**
     *
     * @param configURL - path to configuration file
     */
    public ConfigReader(URL configURL) {
        this();
        this.configURL = configURL;
    }

    public boolean config() {
        boolean gracefulExit = false;
        BufferedReader in = null;
        String inputLine = null;
        File f = new File(configURL.getPath());
        String path = f.getParent() + "/";
        File p = new File(path);

        Map<String, String> fileList = new HashMap<String, String>();
        try {
            in = new BufferedReader(new InputStreamReader(configURL.openStream()));
            while ((inputLine = in.readLine()) != null) {
                inputLine = inputLine.trim();
                if (!inputLine.isEmpty() && !inputLine.startsWith("#")) {
                    String[] opt = inputLine.split(":");
                    if (opt.length == 2) {
                       fileList.put(opt[0].trim(), opt[1].trim());
                    } else {
                        // malformed mapping file
                        throw new IOException("Malformed configuration file: " + configURL);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            gracefulExit = true;
            gate.util.Err.println("Unable to locate file " + configURL);
        } catch (IOException ie) {
            gracefulExit = true;
            gate.util.Err.println("Unable to read file " + configURL);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException iee) {
                    gracefulExit = true;
                }
            }
        }

        Iterator<Map.Entry<String, String>> it = fileList.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> pairs = it.next();
            String key = pairs.getKey();
            // System.out.println("Reading config for: " + key);
            String item = pairs.getValue();
            File fitem = new File(p, item);
            try {
                StringBuilder sb = new StringBuilder();
                FileInputStream fis = new FileInputStream(fitem);
                in = new BufferedReader(new InputStreamReader(fis));
                while ((inputLine = in.readLine()) != null) {
                    inputLine = inputLine.trim();
                    if (!inputLine.isEmpty() && !inputLine.startsWith("#")) {
                        sb.append(inputLine).append("|");
                    }
                }
                if (sb != null) {
                    sb.deleteCharAt(sb.lastIndexOf("|"));
                    options.put(key, sb.toString().trim());
                }
                // System.out.println(key + ": " + options.get(key));
            } catch (FileNotFoundException e) {
                gracefulExit = true;
                gate.util.Err.println("Unable to locate file " + fitem);
            } catch (IOException ie) {
                gracefulExit = true;
                gate.util.Err.println("Unable to read file " + fitem);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException iee) {
                        gracefulExit = true;
                    }
                }
            }
        }

        return gracefulExit;
    }
}
