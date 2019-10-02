// Copyright 2011 The Whiley Project Developers
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package wycc.util;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import wybs.lang.SyntacticException;
import wybs.lang.SyntacticItem;
import wybs.lang.Build.Environment;
import wybs.lang.Build.Project;
import wybs.util.SequentialBuildProject;
import wybs.util.AbstractCompilationUnit.Value;
import wybs.util.AbstractCompilationUnit.Value.UTF8;
import wycc.cfg.ConfigFile;
import wycc.cfg.Configuration;
import wycc.commands.Build;
import wycc.commands.Clean;
import wycc.commands.Config;
import wycc.commands.Help;
import wycc.commands.Inspect;
import wycc.commands.Install;
import wycc.commands.Run;
import wycc.lang.Command;
import wycc.lang.Package;
import wycc.lang.Command.Option;
import wyfs.lang.Content;
import wyfs.lang.Path;
import wyfs.lang.Path.Filter;
import wyfs.lang.Path.ID;
import wyfs.lang.Path.Root;
import wyfs.util.Trie;
import wyfs.util.ZipFile;

public abstract class AbstractWorkspace extends AbstractPluginEnvironment {
	public static final Trie BUILD_PLATFORMS = Trie.fromString("build/platforms");


	/**
	 * Set of default command descriptors.
	 */
	public static final Command.Descriptor[] DESCRIPTORS = {
			Build.DESCRIPTOR, Clean.DESCRIPTOR, Config.DESCRIPTOR, Help.DESCRIPTOR, Install.DESCRIPTOR,
			Inspect.DESCRIPTOR, Run.DESCRIPTOR
	};

	/**
	 * Set of default content types.
	 */
	public static final Content.Type<?>[] CONTENT_TYPES = {
			ConfigFile.ContentType,
			ZipFile.ContentType
	};

	/**
	 * The descriptor for the outermost command.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static Command.Descriptor ROOT_DESCRIPTOR(AbstractWorkspace workspace) {
		// Extract the root descriptors
		List<Command.Descriptor> descriptors = workspace.getCommandDescriptors();
		// Done
		return new Command.Descriptor() {

			@Override
			public Schema getConfigurationSchema() {
				return null;
			}

			@Override
			public List<Option.Descriptor> getOptionDescriptors() {
				return Arrays.asList(
						Command.OPTION_FLAG("verbose", "generate verbose information about the build", false),
						Command.OPTION_FLAG("brief", "generate brief output for syntax errors", false));
			}

			@Override
			public Command initialise(Command.Environment environment) {
				final Command.Descriptor descriptor = this;
				return new Command() {

					@Override
					public Descriptor getDescriptor() {
						return descriptor;
					}

					@Override
					public void initialise() {
					}

					@Override
					public void finalise() {
					}

					@Override
					public boolean execute(Project project, Template template) throws Exception {
						boolean verbose = template.getOptions().get("verbose", Boolean.class);
						try {
							//
							if (template.getChild() != null) {
								// Execute a subcommand
								template = template.getChild();
								// Access the descriptor
								Command.Descriptor descriptor = template.getCommandDescriptor();
								// Construct an instance of the command
								Command command = descriptor.initialise(environment);
								//
								return command.execute(project,template);
							} else {
								// Initialise command
								Command cmd = Help.DESCRIPTOR.initialise(environment);
								// Execute command
								return cmd.execute(project, template);
							}
						} catch (SyntacticException e) {
							SyntacticItem element = e.getElement();
							e.outputSourceError(System.err, false);
							if (verbose) {
								printStackTrace(System.err, e);
							}
							return false;
						} catch (Exception e) {
							// FIXME: do something here??
							e.printStackTrace();
							return false;
						}
					}
				};
			}

			@Override
			public String getName() {
				return "wy";
			}

			@Override
			public String getDescription() {
				return "Command-line interface for the Whiley Compiler Collection";
			}

			@Override
			public List<Command.Descriptor> getCommands() {
				return descriptors;
			}
		};
	}

	// ========================================================================
	// Instance Fields
	// ========================================================================

	/**
	 * List of active projects.
	 */
	private HashMap<Path.ID, AbstractProject> projects = new HashMap<>();

	// ========================================================================
	// Constructor
	// ========================================================================

	public AbstractWorkspace(Configuration configuration) throws IOException {
		super(configuration,new Logger.Default(System.err), ForkJoinPool.commonPool());
		// Add default commands
		commandDescriptors.addAll(Arrays.asList(DESCRIPTORS));
		// Add default content types
		contentTypes.addAll(Arrays.asList(CONTENT_TYPES));
	}

	public Command.Descriptor getCommandRoot() {
		return ROOT_DESCRIPTOR(this);
	}

	/**
	 * Create a project within this environment on which commands can be called.
	 *
	 * @param root
	 * @return
	 * @throws IOException
	 */
	public Command.Project open(Path.ID id) throws IOException {
		AbstractProject project = projects.get(id);
		//
		if (project == null) {
			Path.Root root = getRoot().createRelativeRoot(id);
			// Create a new project record
			project = new AbstractProject(this, root);
			// Configure package directory structure
			project.initialise();
			// Refresh project to initialise build instances
			project.refresh();
			// Resolve all package dependencies
			project.resolve();
			// Retain project record
			projects.put(id, project);
		}
		//
		return project;
	}

	/**
	 * Close all projects within this workspace. This forces them to be flushed to
	 * disk.
	 *
	 * @throws IOException
	 */
	public void closeAll() throws IOException {
		for (AbstractProject project : projects.values()) {
			project.flush();
		}
		projects.clear();
	}

	@Override
	public List<Project> getProjects() {
		return new ArrayList<>(projects.values());
	}

	public class AbstractProject extends SequentialBuildProject implements Command.Project {
		private Configuration configuration = Configuration.EMPTY(EMPTY_SCHEMA);

		public AbstractProject(Environment environment, Root root) {
			super(environment, root);
		}

		/**
		 * Add any declared dependencies to the set of project roots. The challenge here
		 * is that we may need to download, install and compile these dependencies if
		 * they are not currently installed.
		 *
		 * @throws IOException
		 */
		public void resolve() throws IOException {
			// Dig out all the defined dependencies
			List<Path.ID> deps = matchAll(Trie.fromString("dependencies/**"));
			// Determine dependency roots
			List<Pair<String,String>> pairs = new ArrayList<>();
			for (int i = 0; i != deps.size(); ++i) {
				Path.ID dep = deps.get(i);
				// Get dependency name
				String name = dep.get(1);
				// Get version string
				UTF8 version = get(UTF8.class, dep);
				//
				pairs.add(new Pair<>(name,version.toString()));
			}
			// Resolve all dependencies
			List<wybs.lang.Build.Package> pkgs = getPackageResolver().resolve(pairs);
			// Done
			getPackages().addAll(pkgs);
		}

		/**
		 * Setup the various roots based on the target platform(s). This requires going
		 * through and adding roots for all source and intermediate files.
		 *
		 * @throws IOException
		 */
		private void initialise() throws IOException {
			Configuration.Schema[] schemas = new Configuration.Schema[buildPlatforms.size() + commandDescriptors.size()
					+ 1];
			int index = 0;
			schemas[index++] = Package.SCHEMA;
			for (int i = 0; i != buildPlatforms.size(); ++i) {
				wybs.lang.Build.Platform platform = buildPlatforms.get(i);
				schemas[index++] = platform.getConfigurationSchema();
			}
			for (int i = 0; i != commandDescriptors.size(); ++i) {
				Command.Descriptor cmd = commandDescriptors.get(i);
				schemas[index++] = cmd.getConfigurationSchema();
			}
			//
			ConfigFile cfg = root.get(Trie.fromString("wy"), ConfigFile.ContentType).read();
			// Parse configuration
			this.configuration = cfg.toConfiguration(Configuration.toCombinedSchema(schemas), false);
			// initialise platforms
			for (wybs.lang.Build.Platform platform : getTargetPlatforms()) {
				// Apply current configuration
				platform.initialise(configuration, this);
			}
		}

		/**
		 * Get the list of declared target platforms for this project. This is
		 * determined by the attribute "build.platforms" in the project (wy.toml) build
		 * file.
		 *
		 * @return
		 */
		private List<wybs.lang.Build.Platform> getTargetPlatforms() {
			ArrayList<wybs.lang.Build.Platform> targetPlatforms = new ArrayList<>();
			// Ensure target platforms are specified
			if (hasKey(BUILD_PLATFORMS)) {
				Value.UTF8[] targetPlatformNames = get(Value.Array.class, BUILD_PLATFORMS).toArray(Value.UTF8.class);
				// Get list of all build platforms.
				List<wybs.lang.Build.Platform> platforms = getBuildPlatforms();
				// Check each platform for inclusion
				for (int i = 0; i != platforms.size(); ++i) {
					wybs.lang.Build.Platform platform = platforms.get(i);
					// Convert name to UTF8 value (ugh)
					Value.UTF8 name = new Value.UTF8(platform.getName().getBytes());
					// Determine whether is a target platform or not
					if (ArrayUtils.firstIndexOf(targetPlatformNames, name) >= 0) {
						targetPlatforms.add(platform);
					}
				}
			}
			// Done
			return targetPlatforms;
		}

		@Override
		public Schema getConfigurationSchema() {
			return configuration.getConfigurationSchema();
		}

		@Override
		public <T> boolean hasKey(ID key) {
			return configuration.hasKey(key);
		}

		@Override
		public <T> T get(Class<T> kind, ID key) {
			return configuration.get(kind, key);
		}

		@Override
		public <T> void write(ID key, T value) {
			configuration.write(key, value);
		}

		@Override
		public List<ID> matchAll(Filter filter) {
			return configuration.matchAll(filter);
		}

		@Override
		public wycc.lang.Command.Environment getEnvironment() {
			return AbstractWorkspace.this;
		}
	}

	/**
	 * Print a complete stack trace. This differs from Throwable.printStackTrace()
	 * in that it always prints all of the trace.
	 *
	 * @param out
	 * @param err
	 */
	private static void printStackTrace(PrintStream out, Throwable err) {
		out.println(err.getClass().getName() + ": " + err.getMessage());
		for (StackTraceElement ste : err.getStackTrace()) {
			out.println("\tat " + ste.toString());
		}
		if (err.getCause() != null) {
			out.print("Caused by: ");
			printStackTrace(out, err.getCause());
		}
	}
}