package parser;

import parser.Localisation.ValueType;

public class Token {
	public final String type;
	public final String baseType;
	public final String value;
	public final ValueType valueType;
	
	public Token(String type, boolean negative) {
		this(type, null, negative);
	}

	public Token(String type, String value, boolean negative) {
		super();
		if (type != null)
			type = type.toLowerCase();
		if (value != null && value.equalsIgnoreCase("no"))
			negative = !negative;
		if (negative)
			this.type = type + "_false";
		else
			this.type = type;
		if (value != null)
			this.value = value.replace("\"", "");
		else
			this.value = null;
		baseType = type;
		valueType = Localisation.getValueType(type, value);
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
		// TODO: Find a way to properly handle the possible operators
		// Consider dynamic formatting; inserting "equals", "greater than", etc. via code
		int index = s.indexOf('=');
		index = s.indexOf('>') != -1 ? s.indexOf('>') : index;
		if (s.indexOf('<') != -1) {
			index = s.indexOf('<');
			negative = !negative;
		}
		if (index == -1)
			token = new Token(s, negative);
		else
			token = new Token(s.substring(0, index).trim(), s.substring(index + 1).trim(), negative);
		return token;
	}
	
	public String getLocalisedValue() {
		return Localisation.formatValue(this);
	}

	public String toString() {
		return Localisation.formatToken(this);
	}
}
