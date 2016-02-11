package parser;

import java.util.Arrays;

import parser.Localisation.ValueType;
import parser.Localisation.Operator;

public class Token {
	public final String type, baseType, value;
	public final ValueType valueType;
	public final Operator operator;
	
	public Token(String type, boolean negative, String parentType) {
		this(type, null, null, negative, parentType);
	}

	public Token(String type, String value, Operator operator, boolean negative, String parentType) {
		super();
		if (type != null)
			type = type.toLowerCase();
		if (value != null && value.equalsIgnoreCase("no"))
			negative = !negative;
		String[] vals = ParsingBlock.parentExceptions.get(parentType);
		baseType = type;
		if (vals != null && Arrays.asList(vals).contains(type)) {
			type = parentType + "_" + type;
		}
		this.type = type;
		if (value != null)
			this.value = value.replace("\"", "");
		else
			this.value = null;
		valueType = Localisation.getValueType(type, value);
		if (negative && operator != null) // Opposite version is offset by one
			operator = Operator.values()[operator.ordinal() + 1];
		this.operator = operator;
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
	public static Token tokenize(String s, boolean negative, String parentType) {
		Token token;
		
		Operator operator = null;
		int index = -1;
		if (s.indexOf('=') != -1) {
			operator = Operator.EQUAL;
			index = s.indexOf('=');
		} else if (s.indexOf('<') != -1) {
			operator = Operator.LESS;
			index = s.indexOf('<');
		} else if (s.indexOf('>') != -1) {
			operator = Operator.MORE;
			index = s.indexOf('>');
		}
		if (index == -1)
			token = new Token(s, negative, parentType);
		else
			token = new Token(s.substring(0, index).trim(), s.substring(index + 1).trim(), operator, negative, parentType);
		return token;
	}
	
	public String getLocalisedValue() {
		return Localisation.formatValue(this);
	}

	public String toString() {
		return Localisation.formatToken(this);
	}
}
