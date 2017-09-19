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

import java.io.PrintStream;

import wycc.util.Logger;

/**
 * Provides a standard interface for logging messages generated by builders.
 * This includes syntax errors, warnings and miscellaneous debugging output.
 *
 * @author David J. Pearce
 */
public interface Logger {

	/**
	 * Log a message, along with a time. The time is used to indicate how long
	 * it took for the action being reported. This is used primarily to signal
	 * that a given stage has been completed in a certain amount of time.
	 *
	 * @param msg
	 * @param time --- total time taken for stage
     * @param time --- difference in available free memory
	 */
	public void logTimedMessage(String msg, long time, long memory);

	/**
	 * The NULL logger simply drops all logged messages. It's a simple, albeit
	 * not that helpful, default.
	 */
	public static final Logger NULL = new Logger() {
		@Override
		public void logTimedMessage(String msg, long time, long memory) {
			// do nothing.
		}
	};

	/**
	 * A simple implementation of <code>Logger</code> which writes to a given
	 * <code>PrintStream</code>.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Default implements Logger {
		private PrintStream logout;

		public Default(PrintStream out) {
			logout=out;
		}
		/**
		 * This method is just a helper to format the output
		 */
		@Override
		public void logTimedMessage(String msg, long time, long memory) {
			logout.print(msg);
			logout.print(" ");
			double mem = memory;
			mem = mem / (1024*1024);
			memory = (long) mem;
			String stats = " [" + Long.toString(time) + "ms";
			if(memory > 0) {
				stats += "+" + Long.toString(memory) + "mb]";
			} else if(memory < 0) {
				stats += Long.toString(memory) + "mb]";
			} else {
				stats += "]";
			}
			for (int i = 0; i < (90 - msg.length() - stats.length()); ++i) {
				logout.print(".");
			}
			logout.println(stats);
		}

		public void logTotalTime(String msg, long time, long memory) {
			memory = memory / 1024;

			for (int i = 0; i <= 90; ++i) {
				logout.print("=");
			}

			logout.println();

			logout.print(msg);
			logout.print(" ");

			double mem = memory;
			mem = mem / (1024*1024);
			memory = (long) mem;
			String stats = " [" + Long.toString(time) + "ms";
			if(memory > 0) {
				stats += "+" + Long.toString(memory) + "mb]";
			} else if(memory < 0) {
				stats += Long.toString(memory) + "mb]";
			} else {
				stats += "]";
			}

			for (int i = 0; i < (90 - msg.length() - stats.length()); ++i) {
				logout.print(".");
			}

			logout.println(stats);
		}
	};
}
