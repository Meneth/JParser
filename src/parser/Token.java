package parser;

public class Token {
	public final String type;
	public final String value;

	public Token(String type, boolean negative) {
		this(type, null, negative);
	}
	
	public Token(String type, String value, boolean negative) {
		super();
		if (negative)
			this.type = type + "_false";
		else
			this.type = type;
		this.value = value;
	}

	public static Token tokenize(String s, boolean negative) {
		Token token;
		int index = s.indexOf('=');
		if (index == -1)
			token = new Token(s, negative);
		else
			token = new Token(s.substring(0, index).trim(), s.substring(
					index + 1).trim(), negative);
		return token;
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
