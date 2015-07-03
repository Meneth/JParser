package parser;

public class Token {
	public final String type;
	public final String baseType;
	public final String value;
	
	public Token(String type, boolean negative) {
		this(type, null, negative);
	}

	public Token(String type, String value, boolean negative) {
		super();
		if (value != null && value.equalsIgnoreCase("no"))
			negative = !negative;
		if (negative)
			this.type = type + "_false";
		else
			this.type = type;
		this.value = value;
		baseType = type;
	}

	/**
	 * Creates from a string a new token and returns it
	 * 
	 * @param s
	 *            The string to be tokenized
	 * @param negative
	 *            Whether or not the token has been inverted by surrounding code
	 * @return A token generated from the string
	 */
	public static Token tokenize(String s, boolean negative) {
		Token token;
		int index = s.indexOf('=');
		if (index == -1)
			token = new Token(s, negative);
		else
			token = new Token(s.substring(0, index).trim(), s.substring(index + 1).trim(), negative);
		return token;
	}
	
	public String getLocalisedValue() {
		return Localisation.formatValue(type, value);
	}

	public String toString() {
		return Localisation.formatToken(type, value);
	}
}
