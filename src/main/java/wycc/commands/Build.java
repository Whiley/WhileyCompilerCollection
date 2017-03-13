package wycc.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import wycc.lang.Command;
import wyfs.lang.Content;
import wyfs.lang.Path;

public class Build implements Command<String> {
	public Build() {

	}

	@Override
	public String getDescription() {
		return "Perform build operations on an existing project";
	}

	public String describeInit() {
		return "Initialise a new build project";
	}

	@Override
	public String execute(String... args) {
		return "BUILDING...";
	}

	@Override
	public String[] getOptions() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String describe(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void set(String name, Object value) throws ConfigurationError {
		// TODO Auto-generated method stub

	}

	@Override
	public Object get(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}
	// =======================================================================
	// Helpers
	// =======================================================================

	/**
	 * Generate the list of source files which need to be recompiled. By
	 * default, this is done by comparing modification times of each whiley file
	 * against its corresponding wyil file. Wyil files which are out-of-date are
	 * scheduled to be recompiled.
	 *
	 * @return
	 * @throws IOException
	 */
	public static <T, S> List<Path.Entry<T>> getModifiedSourceFiles(Path.Root sourceDir,
			Content.Filter<T> sourceIncludes, Path.Root binaryDir, Content.Type<S> binaryContentType)
					throws IOException {
		// Now, touch all source files which have modification date after
		// their corresponding binary.
		ArrayList<Path.Entry<T>> sources = new ArrayList<>();

		for (Path.Entry<T> source : sourceDir.get(sourceIncludes)) {
			// currently, I'm assuming everything is modified!
			Path.Entry<S> binary = binaryDir.get(source.id(), binaryContentType);
			// first, check whether wyil file out-of-date with source file
			if (binary == null || binary.lastModified() < source.lastModified()) {
				sources.add(source);
			}
		}

		return sources;
	}
}
