package parser;

public class Token {
	public final String type;
	public final String value;

	public Token(String type) {
		super();
		this.type = type;
		value = null;
	}

	public static Token tokenize(String s) {
		Token token;
		int index = s.indexOf('=');
		if (index == -1)
			token = new Token(s);
		else
			token = new Token(s.substring(0, index).trim(), s.substring(
					index + 1).trim());
		return token;
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
		String output = ParsingBlock.getStatement(type);
		if (output == null)
			return type + ": " + value;
		if (output.contains("%s"))
			return String.format(output, value);
		return output;
	}
}
