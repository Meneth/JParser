package parser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;

public class IO {
	public static LinkedList<String> readFile(String fileName) throws IOException {
		LinkedList<String> lines = new LinkedList<>();
		BufferedReader in = new BufferedReader(new FileReader(fileName));
		String line = in.readLine();
		while (line != null) {
			// Get rid of comments
			int commentIndex = line.indexOf('#');
			if (commentIndex != -1)
				line = line.substring(0, commentIndex);
			
			// Handle brackets
			line = line.replace("{", "{\n");
			line = line.replace("}", "\n}");
			
			// Handle multiple actions in one line
			line = line.replaceAll("(=[\\s]*\"?[\\w ]*\"?) ([\\w]*[\\s]*=)", "$1\n$2");
			
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
	
	public static void main(String[] args) {
		try {
			LinkedList<String> list = readFile("flavorKOL.txt");
			for (String s : list)
				System.out.println(s);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}