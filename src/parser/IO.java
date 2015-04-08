package parser;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.LinkedList;

public class IO {
	public static BufferedReader getReader(String fileName) throws FileNotFoundException {
		return new BufferedReader(new FileReader(fileName));
	}
	
	/**
	 * Reads a PDX-script file, reducing it to only the statements therein.
	 * Strips out comments, blank lines, and similar, and splits multiple statements on one line over multiple lines
	 * @param fileName Name of the PDX-script file to be read. Full file path or relative path
	 * @return Linked list consisting of the processed output
	 * @throws IOException
	 */
	public static LinkedList<String> readFile(String fileName) throws IOException {
		LinkedList<String> lines = new LinkedList<>();
		BufferedReader in = getReader(fileName);
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
			String value = "=[\\s]*[\\w]*";
			String quotedValue = "=[\\s]*\"[\\w ]*\"";
			
			line = line.replaceAll("(" + value + "|" + quotedValue + ") ([\\w]*[\\s]*=)", "$1\n$2");
			
			int start = 0;
			int end = line.indexOf('\n');
			String s;
			do { // Substring and indexOf are used as it is faster than StringTokenizer or split
				try { s = line.substring(start, end); }
				catch (StringIndexOutOfBoundsException e) {
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
	 * @param fileName Name of the localisation file to be read. Full file path or relative path
	 * @param map Map to add the localisation to, rather than returning a map, as one might often want to read several files into one map
	 * @throws IOException
	 */
	public static void readLocalisation(String fileName, Map<String, String> map) throws IOException {
		BufferedReader in = getReader(fileName);
		String line = in.readLine();
		while (line != null) {
			line = line.trim().replace("\"", "");
			int index = line.indexOf(": ");
			if (index == -1) {
				line = in.readLine();
				continue;
			}
			String key = line.substring(0, index);
			// ": " used as delimiter, so index + 2
			String value = line.substring(index + 2);
			map.put(key, value);
			line = in.readLine();
		}
	}
	
	/**
	 * Reads a YAML-esque exceptions file. Does not handle nesting
	 * @param fileName Name of the file to be read. Full file path or relative path
	 * @param map Map to add the exceptions to
	 * @throws IOException
	 */
	public static void readExceptions(String fileName, Map<String, String> map) throws IOException {
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
			for (String key: keys) {
				map.put(key, value);
			}
			line = in.readLine();
		}
	}
	
	public static void main(String[] args) {
		try {
			LinkedList<String> list = readFile("cleanup.txt");
			for (String s : list)
				System.out.println(s);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}