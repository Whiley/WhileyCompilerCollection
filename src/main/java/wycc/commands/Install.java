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
package wycc.commands;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import wybs.util.AbstractCompilationUnit.Value;
import wycc.cfg.Configuration;
import wycc.cfg.Configuration.Schema;
import wycc.lang.Command;
import wycc.util.Logger;
import wyfs.lang.Content;
import wyfs.lang.Path;
import wyfs.util.Trie;
import wyfs.util.ZipFile;
import wybs.lang.Build;

public class Install implements Command {
	public static final Trie BUILD_INCLUDES = Trie.fromString("build/includes");
	public static final Trie BUILD_PLATFORM_INCLUDES = Trie.fromString("build/*/includes");

	/**
	 * The descriptor for this command.
	 */
	public static final Command.Descriptor DESCRIPTOR = new Command.Descriptor() {
		@Override
		public String getName() {
			return "install";
		}

		@Override
		public String getDescription() {
			return "Install package into local repository";
		}

		@Override
		public List<Option.Descriptor> getOptionDescriptors() {
			return Collections.EMPTY_LIST;
		}

		@Override
		public Schema getConfigurationSchema() {
			return Configuration.EMPTY_SCHEMA;
		}

		@Override
		public List<Descriptor> getCommands() {
			return Collections.EMPTY_LIST;
		}

		@Override
		public Command initialise(Command.Environment environment) {
			return new Install(environment, System.out, System.err);
		}

	};

	/**
	 * Provides a generic place to which error output should be directed. This
	 * should eventually be replaced.
	 */
	private Logger logger;

	/**
	 * The enclosing environment for this command
	 */
	private final Command.Environment environment;

	/**
	 * Provides a generic place to which normal output should be directed. This
	 * should eventually be replaced.
	 */
	private final PrintStream sysout;

	/**
	 * List of include filters
	 */
	private final ArrayList<Value.UTF8> includes;

	public Install(Command.Environment environment, OutputStream sysout, OutputStream syserr) {
		this.environment = environment;
		this.logger = environment.getLogger();
		this.sysout = new PrintStream(sysout);
		this.includes = new ArrayList<>();
		// Determine default included files
		Value.UTF8[] items = environment.get(Value.Array.class, BUILD_INCLUDES).toArray(Value.UTF8.class);
		this.includes.addAll(Arrays.asList(items));
		// Determine platform-specific included files
		for(Path.ID id : environment.matchAll(BUILD_PLATFORM_INCLUDES)) {
			items = environment.get(Value.Array.class, id).toArray(Value.UTF8.class);
			includes.addAll(Arrays.asList(items));
		}
	}

	@Override
	public Descriptor getDescriptor() {
		return DESCRIPTOR;
	}

	@Override
	public void initialise() {
		// Nothing to do here?
	}

	@Override
	public void finalise() {
		// Nothing to do here?
	}

	@Override
	public boolean execute(Template template) {
		try {
			// Determine list of files to go in package
			List<Path.Entry<?>> files = determinePackageContents();
			// Construct zip file context representing package
			ZipFile zf = createZipFile(files);
			// Determine the target location for the package file
			Path.Entry<ZipFile> target = getPackageFile();
			// Physically write out the zip file
			target.write(zf);
			// Flush it to disk
			target.flush();
			// Done
			sysout.println("installed " + target.location() + " ... " + files.size() + " file(s)");
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Identify which files are to be included in the package. This is determined by
	 * the build/includes attribute in the package manifest.
	 *
	 * @return
	 * @throws IOException
	 */
	private List<Path.Entry<?>> determinePackageContents() throws IOException {
		// Determine includes filter
		ArrayList<Path.Entry<?>> files = new ArrayList<>();
		// Iterator all active projects
		for(Build.Project project : environment.getProjects()) {
			// Extract root of this project
			Path.Root root = project.getRoot();
			// Add all files from the includes filter
			for(int i=0;i!=includes.size();++i) {
				// Construct a filter from the attribute itself
				Content.Filter filter = createFilter(includes.get(i).toString());
				// Add all files matching the attribute
				files.addAll(root.get(filter));
			}
		}
		// Done
		return files;
	}

	/**
	 * Given a list of files construct a corresponding ZipFile containing them.
	 *
	 * @param files
	 * @return
	 * @throws IOException
	 */
	private ZipFile createZipFile(List<Path.Entry<?>> files) throws IOException {
		// The set of known paths
		HashSet<Path.ID> paths = new HashSet<>();
		// The zip file we're creating
		ZipFile zf = new ZipFile();
		// Add each file to zip file
		for (int i = 0; i != files.size(); ++i) {
			long start = System.currentTimeMillis();
			Path.Entry<?> file = files.get(i);
			// Extract path
			addPaths(file.id().parent(), paths, zf);
			// Construct filename for given entry
			String filename = file.id().toString() + "." + file.contentType().getSuffix();
			// Extract bytes representing entry
			byte[] contents = readFileContents(file);
			zf.add(new ZipEntry(filename), contents);
			long time = System.currentTimeMillis() - start;
			logger.logTimedMessage("Packaging " + file.id() + "." + file.contentType().getSuffix(), time, 0);
		}
		//
		return zf;
	}

	/**
	 * This is a slightly strange method. Basically, it recursively adds directory
	 * entries into the ZipFile. Technically such entries should not be needed.
	 * However, due to a quirk in the way that ZipFileRoot works (in fact,
	 * AbstractFolder to be more precise) these entries are required for now.
	 *
	 * @param path
	 * @param paths
	 * @param zf
	 */
	private void addPaths(Path.ID path, HashSet<Path.ID> paths, ZipFile zf) {
		if(path.size() > 0 && !paths.contains(path)) {
			addPaths(path.parent(),paths,zf);
			// A new path encountered
			String directory = path.toString() + "/";
			zf.add(new ZipEntry(directory), new byte[0]);
			paths.add(path);
		}
	}

	/**
	 * Read the contents of a given file into a byte array.
	 *
	 * @param file
	 * @return
	 * @throws IOException
	 */
	private byte[] readFileContents(Path.Entry<?> file) throws IOException {
		InputStream in = file.inputStream();
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int nRead;
		// Read bytes in max 1024 chunks
		byte[] data = new byte[1024];
		// Read all bytes from the input stream
		while ((nRead = in.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, nRead);
		}
		// Done
		buffer.flush();
		return buffer.toByteArray();
	}

	/**
	 * Construct an entry for the target package (zip) file.
	 *
	 * @return
	 * @throws IOException
	 */
	private Path.Entry<ZipFile> getPackageFile() throws IOException {
		// Extract package name from configuration
		Value.UTF8 name = environment.get(Value.UTF8.class, Trie.fromString("package/name"));
		// Extract package version from
		Value.UTF8 version = environment.get(Value.UTF8.class, Trie.fromString("package/version"));
		// Determine fully qualified package name
		Trie pkg = Trie.fromString(name + "-v" + version);
		// Dig out the file!
		return environment.getRepositoryRoot().create(pkg, ZipFile.ContentType);
	}

	/**
	 * Create a content filter from the string representation.
	 *
	 * @param filter
	 * @return
	 */
	private Content.Filter createFilter(String filter) {
		String[] split = filter.split("\\.");
		//
		Content.Type contentType = getContentType(split[1]);
		//
		return Content.filter(split[0], contentType);
	}

	/**
	 * Determine the content type from the suffix of a given build/includes
	 * attribute entry.
	 *
	 * @param suffix
	 * @return
	 */
	private Content.Type getContentType(String suffix) {
		Content.Type<?> ct = environment.getContentRegistry().contentType(suffix);
		//
		if(ct != null) {
			return ct;
		} else {
			// miss
			throw new IllegalArgumentException("unknown content-type: " + suffix);
		}
	}
}
