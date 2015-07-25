package parser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.LinkedList;

public class IO {
	public static BufferedReader getReader(String fileName) throws IOException {
		return new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "UTF8"));
	}
	
	public static BufferedReader getANSIReader(String fileName) throws IOException {
		return new BufferedReader(new InputStreamReader(new FileInputStream(fileName), "Cp1252"));
	}

	/**
	 * Reads a PDX-script file, reducing it to only the statements therein.
	 * Strips out comments, blank lines, and similar, and splits multiple
	 * statements on one line over multiple lines
	 * 
	 * @param fileName
	 *            Name of the PDX-script file to be read. Full file path or
	 *            relative path
	 * @return Linked list consisting of the processed output
	 * @throws IOException
	 */
	public static LinkedList<String> readFile(String fileName) throws IOException {
		LinkedList<String> lines = new LinkedList<>();
		BufferedReader in = getANSIReader(fileName);
		String line = in.readLine();
		while (line != null) {
			line = line.trim();
			// Get rid of comments
			int commentIndex = line.indexOf('#');
			if (commentIndex != -1)
				line = line.substring(0, commentIndex);

			// Handle brackets
			line = line.replace("{", "{\n");
			line = line.replace("}", "\n}");

			// Handle multiple actions in one line
			line = line.replaceAll("([\\w.\"])\\s+(\\w+\\s*=)", "$1\n$2");

			int start = 0;
			int end = line.indexOf('\n');
			String s;
			do { // Substring and indexOf are used as it is faster than
					// StringTokenizer or split
				try {
					s = line.substring(start, end);
				} catch (StringIndexOutOfBoundsException e) {
					s = line.substring(start); // Final part
				}
				// Get rid of whitespace
				s = s.trim();
				if (!s.equals(""))
					lines.add(s);
				start = end + 1;
				end = line.indexOf('\n', start + 1);
			} while (start != 0);
			// Go onto next line
			line = in.readLine();
		}
		in.close();
		return lines;
	}

	/**
	 * Reads a YAML localisation file. Does not handle nesting
	 * 
	 * @param fileName
	 *            Name of the localisation file to be read. Full file path or
	 *            relative path
	 * @param map
	 *            Map to add the localisation to, rather than returning a map,
	 *            as one might often want to read several files into one map
	 * @param ignoreQuotes
	 *            If set to true, all quote-signs will be stripped out
	 * @throws IOException
	 */
	public static void readLocalisation(String fileName, Map<String, String> map,
			boolean ignoreQuotes) throws IOException {
		BufferedReader in = getReader(fileName);
		String line = in.readLine();
		while (line != null) {
			line = line.trim();
			if (ignoreQuotes)
				line = line.replace("\"", "");
			int index = line.indexOf(": ");
			if (index == -1) {
				line = in.readLine();
				continue;
			}
			String key = line.substring(0, index).toLowerCase();
			// ": " used as delimiter, so index + 2
			String value = line.substring(index + 2);
			map.put(key, value);
			line = in.readLine();
		}
	}

	/**
	 * Reads a YAML-esque exceptions file. Does not handle nesting
	 * 
	 * @param fileName
	 *            Name of the file to be read. Full file path or relative path
	 * @param map
	 *            Map to add the exceptions to
	 * @throws IOException
	 */
	public static void readExceptions(String fileName, Map<String, String[]> map) throws IOException {
		BufferedReader in = getReader(fileName);
		String line = in.readLine();
		while (line != null) {
			line = line.trim();
			int index = line.indexOf(": ");
			if (index == -1) {
				line = in.readLine();
				continue;
			}
			String key = line.substring(0, index);
			// ": " used as delimiter, so index + 2
			String[] values = line.substring(index + 2).split(", ");
			map.put(key, values);
			line = in.readLine();
		}
	}
	
	/**
	 * Reads a YAML-esque exceptions file. Does not handle nesting
	 * 
	 * @param fileName
	 *            Name of the file to be read. Full file path or relative path
	 * @param map
	 *            Map to add the exceptions to
	 * @throws IOException
	 */
	public static void readLookupRules(String fileName, Map<String, String> map) throws IOException {
		BufferedReader in = getReader(fileName);
		String line = in.readLine();
		while (line != null) {
			line = line.trim();
			int index = line.indexOf(": ");
			if (index == -1) {
				line = in.readLine();
				continue;
			}
			String value = line.substring(0, index);
			// ": " used as delimiter, so index + 2
			String[] keys = line.substring(index + 2).split(", ");
			for (String key : keys) {
				map.put(key, value);
			}
			line = in.readLine();
		}
	}

	/**
	 * Writes a collection of strings to a file
	 * 
	 * @param fileName
	 * @param contents
	 * @throws IOException
	 */
	public static void writeFile(String fileName, Collection<String> contents) throws IOException {
		BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
				fileName), "UTF8"));
		for (String string : contents) {
			out.write(string + "\n");
		}
		out.close();
	}

	public static void readHeaders(String fileName, Collection<String> headerList, int level)
			throws IOException {
		File f = new File(fileName);
		Collection<String> file;
		if (f.isFile())
			file = readFile(fileName);
		else {
			file = new LinkedList<>();
			for (Object fi : Files.walk(Paths.get(fileName)).toArray()) {
				if (((Path) fi).toFile().isFile())
					file.addAll(readFile(fi.toString()));
			}
		}
		int nesting = 0;
		for (String line : file) {
			line = line.trim().toLowerCase();
			if (line.endsWith("{")) {
				if (nesting == level)
					headerList.add(line.split("=")[0].trim().toLowerCase());
				nesting++;
			} else if (line.equals("}"))
				nesting--;
		}
	}
}