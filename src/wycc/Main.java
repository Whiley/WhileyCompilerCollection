package wycc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import wycc.lang.Logger;
import wycc.lang.Plugin;

public class Main {
	
	/**
	 * Identifies the location where plugins are stored.
	 */
	public static final String PLUGINS_DIR = "lib/plugins/";
	
	public static final String METADATA_FILE = "plugin.xml";
	
	public static void main(String[] args) {
		ArrayList<Plugin> plugins = new ArrayList<Plugin>();
		
		scanForPlugins(PLUGINS_DIR,plugins);
				
		URL[] urls = plugins.toArray(new URL[plugins.size()]);
		URLClassLoader loader = new URLClassLoader(urls);
		System.out.println("FOUND: " + loader.findResource("wydefault.Activator"));
		try {
			System.out.println(Arrays.toString(urls));
			Class c = loader.loadClass("wydefault.Activator");
			Method m = c.getMethod("activate");
			Object self = c.newInstance();
			m.invoke(self);			
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Scan a given directory for plugins. A plugin is a jar file which contains
	 * an appropriate plugin.xml file. This method does not start any plugins,
	 * it simply extracts the appropriate meta-data from their plugin.xml file.
	 * 
	 * @param directory
	 *            Directory to scan for plugins.
	 * @param plugins
	 *            List of plugins to which any plugins found will be added.
	 */
	private static void scanForPlugins(String directory, ArrayList<Plugin> plugins) {
		File pluginDir = new File(directory);
		for (String n : pluginDir.list()) {
			if (n.endsWith(".jar")) {
				try {
					URL url = new File(directory + File.separator + n).toURI()
							.toURL();
					Plugin plugin = extractMetaData(url);
					if(plugin != null) {
						plugins.add(plugin);
					}
				} catch (MalformedURLException e) {
					// This basically shouldn't happen, since we're constructing
					// the URL directly from the directory name and the name of
					// a located file.
				}
			}
		}
	}
	
	/**
	 * Open a given plugin Jar and attempt to extract the plugin.xml file. If
	 * this exists, then read it and extract appropriate meta-data.
	 * 
	 * @param pluginURL
	 * @return
	 */
	private static Plugin extractMetaData(URL pluginURL) {
		try {
			JarInputStream jarIn = new JarInputStream(new FileInputStream(
					pluginURL.getFile()));
			JarEntry entry;
			while((entry = jarIn.getNextJarEntry()) != null) {
				if(entry.getName().equals(METADATA_FILE)) {
					System.out.println("GOT HERE");
				}
			}			
		} catch (IOException e) {
			// Just ignore this jar file ... something is wrong.
		}
		return null;
	}
}
