package parser;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class Parsing {
	private static final Map<String, String> statements = new HashMap<>();
	
	public static Collection<String> parseBlock(Block block, Collection<String> output) {
		int nesting = 0, i = 0, start = 0;
		String type = null;
		for (String s : block) {
			if (s.endsWith("{")) {
				nesting++;
				if (nesting == 1) {
					type = s.substring(0, s.indexOf('=')).trim();
					start = i; // Start of new block
				}
			}
			else if (s.equals("}")) {
				nesting--;
				if (nesting == 0) {
					parseBlock(new Block(type, block, block.subList(start + 1, i)), output);
				}
			}
			else if (nesting == 0) {
				output(s, output);
			}
			i++;
		}
		return output;
	}

	private static void output(String s, Collection<String> output) {
		Token token;
		int index = s.indexOf('=');
		if (index == -1)
			token = new Token(s.substring(0, index).trim());
		else
			token = new Token(s.substring(0, index).trim(), s.substring(index + 1).trim());
		output.add(token.toString());
	}
	
	static String getStatement(String statement) {
		return statements.get(statement);
	}
	
	public static void main(String[] args) {
		try {
			IO.readLocalisation("statements/statements.txt", statements);
			LinkedList<String> list = IO.readFile("cleanup.txt");
			Collection<String> output = parseBlock(new Block(null, null, list), new LinkedList<String>());
			for (String string : output) {
				System.out.println(string);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

class Block extends LinkedList<String> {
	private static final long serialVersionUID = -5279580549902168417L;
	public final String type;
	public final Block parent;
	
	public Block(String type, Block parent, Collection<String> contents) {
		super(contents);
		this.type = type;
		this.parent = parent;
	}
}

class Token {
	private final String type;
	private final String value;
	
	public Token(String type) {
		super();
		this.type = type;
		value = null;
	}

	public Token(String type, String value) {
		super();
		this.type = type;
		this.value = value;
	}
	
	public String toString() {
		return lookup(type, value);
	}

	private String lookup(String type, String value) {
		String output = Parsing.getStatement(type);
		if (output != null && output.contains("%s"))
			return String.format(output, value);
		return output;
	}
}