package wycc.util;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import wycc.lang.Logger;
import wycc.lang.Plugin;
import wycc.lang.PluginActivator;
import wycc.lang.PluginContext;

public class DefaultPluginManager {
	
	/**
	 * Logging stream, which is null by default.
	 */
	private Logger logger = Logger.NULL;
	
	/**
	 * The list of locations into where we will search for plugin
	 */
	private ArrayList<String> locations = new ArrayList<String>(); 
	
	/**
	 * The list of activated plugins
	 */
	private ArrayList<Plugin> plugins = new ArrayList<Plugin>();
	
	/**
	 * The plugin context used to manage extension points for plugins.
	 * 
	 * @param locations
	 */
	private PluginContext context;
	
	public DefaultPluginManager(PluginContext context,
			Collection<String> locations) {
		this.locations.addAll(locations);
		this.context = context;
	}
	
	public void setLogger(Logger logger) {
		this.logger = logger;
	}
	
	/**
	 * Scan and activate all plugins on the search path. As part of this, all
	 * plugin dependencies will be checked.
	 */
	public void start() {
		// First, scan for any plugins in the given directory.
		scan();
		
		// Second, construct the URLClassLoader which will be used to load
		// classes within the plugins.		
		URL[] urls = new URL[plugins.size()];
		for(int i=0;i!=plugins.size();++i) {
			urls[i] = plugins.get(i).getLocation();
		}
		URLClassLoader loader = new URLClassLoader(urls);
		
		// Third, active the plugins. This will give them the opportunity to
		// register whatever extensions they like.
		activatePlugins(loader);
	}
	
	/**
	 * Deactivate all plugins previously activated.
	 */
	public void stop() {
		deactivatePlugins();
	}
	
	/**
	 * Activate all plugins in the order of occurrence in the given list. It is
	 * assumed that all dependencies are already resolved prior to this and all
	 * plugins are topologically sorted.
	 */
	private void activatePlugins(URLClassLoader loader) {
		for (int i=0;i!=plugins.size();++i) {
			Plugin plugin = plugins.get(i);
			try {
				Class c = loader.loadClass(plugin.getActivator());				
				PluginActivator self = (PluginActivator) c.newInstance();
				self.start(context);	
				logger.logTimedMessage("Activated plugin " + plugin.getId()
						+ " v" + plugin.getVersion() , 0, 0);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Deactivate all plugins in the reverse order of occurrence in the given
	 * list. It is assumed that all dependencies are already resolved prior to
	 * this and all plugins are topologically sorted.
	 */
	private void deactivatePlugins() {
		
		// TODO!
		
	}
	
	/**
	 * Scan a given directory for plugins. A plugin is a jar file which contains
	 * an appropriate plugin.xml file. This method does not start any plugins,
	 * it simply extracts the appropriate meta-data from their plugin.xml file.
	 */
	private void scan() {
		for(String location : locations) {
			File pluginDir = new File(location);
			if (pluginDir.exists() && pluginDir.isDirectory()) {
				for (String n : pluginDir.list()) {
					if (n.endsWith(".jar")) {
						try {
							URL url = new File(location + File.separator + n)
									.toURI().toURL();
							Plugin plugin = parsePluginManifest(url);
							if (plugin != null) {
								plugins.add(plugin);
							}
						} catch (MalformedURLException e) {
							// This basically shouldn't happen, since we're
							// constructing
							// the URL directly from the directory name and the
							// name of
							// a located file.
						}
					}
				}
			}
		}
		logger.logTimedMessage("Found " + plugins.size() + " plugins", 0,0);		
	}
	
	/**
	 * Open a given plugin Jar and attempt to extract the plugin meta-data from
	 * the manifest. If the manifest doesn't contain the appropriate
	 * information, then it's ignored an null is returned.
	 * 
	 * @param bundleURL
	 * @return
	 */
	private static Plugin parsePluginManifest(URL bundleURL) {
		try {
			JarFile jarFile = new JarFile(bundleURL.getFile());
			Manifest manifest = jarFile.getManifest();
			Attributes attributes = manifest.getMainAttributes();
			String bundleName = attributes.getValue("Bundle-Name");
			String bundleId = attributes.getValue("Bundle-SymbolicName");
			Plugin.Version bundleVersion = new Plugin.Version(
					attributes.getValue("Bundle-Version"));
			String bundleActivator = attributes.getValue("Bundle-Activator");
			List<Plugin.Dependency> bundleDependencies = Collections.EMPTY_LIST;
			return new Plugin(bundleName, bundleId, bundleVersion, bundleURL,
					bundleActivator, bundleDependencies);
		} catch (IOException e) {
			// Just ignore this jar file ... something is wrong.
		}
		return null;
	}
}