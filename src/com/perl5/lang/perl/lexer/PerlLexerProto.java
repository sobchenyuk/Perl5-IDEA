package com.perl5.lang.perl.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.perl5.lang.perl.lexer.elements.PerlArray;
import com.perl5.lang.perl.lexer.elements.PerlGlob;
import com.perl5.lang.perl.lexer.elements.PerlHash;
import com.perl5.lang.perl.lexer.elements.PerlScalar;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by hurricup on 19.04.2015.
 */
public abstract class PerlLexerProto implements FlexLexer, PerlTokenTypes
{
	// JFlex generated methods
	public abstract CharSequence yytext();
	public abstract void yypushback(int number);

	// My JFlex upgrades
	public abstract int getNextTokenStart();
	public abstract void setTokenStart(int position);
	public abstract boolean isLastToken();

	// Lexer state changes (we don't know LEX constants in advance
	public abstract void yybegin_LEX_MULTILINE();
	public abstract void yybegin_YYINITIAL();
	public abstract void yybegin_LEX_MULTILINE_TOKEN();

	/**
	 *  Multiline part <<'smth'
	 **/

	/** set if prepared for multiline beginning on newline **/
	protected boolean waitingMultiLine = false;

	/** pre-set multiline type, depends on opener **/
	protected IElementType declaredMultiLineType;

	/** stored multiline start position **/
	protected int multiLineStart;

	/** contains marker for multiline end **/
	protected String multilineMarker;

	protected Pattern markerPattern = Pattern.compile("<<\\s*['\"]?([^\"\']+)['\"]?");

	/**
	 * Invoken on opening token, waiting for a newline
	 * @param lineType - future line type: single or double quoted
	 */
	protected void prepareForMultiline(IElementType lineType)
	{
		waitingMultiLine = true;

		String openToken = yytext().toString();
		Matcher m = markerPattern.matcher(openToken);
		if (m.matches())
		{
			multilineMarker = m.group(1);
			System.out.printf("Got opening token: `%s`\n", multilineMarker);
		} else // shouldn't happen
		{
			throw new Error("Could not match opening multiline marker: " + openToken);
		}

		// #@todo We should find a proper way to do this, can have format and be DQ/SQ
		if (multilineMarker.equals("HTML"))
		{
			lineType = PERL_MULTILINE_HTML;
		}
		else if (multilineMarker.equals("XML"))
		{
			lineType = PERL_MULTILINE_XML;
		}

		declaredMultiLineType = lineType;

	}

	protected boolean isWaitingMultiLine(){return waitingMultiLine;}

	/**
	 * Starts multiline reading
	 */
	protected void startMultiLine()
	{
		multiLineStart = getNextTokenStart();
		yybegin_LEX_MULTILINE();
		waitingMultiLine = false;
		System.out.printf("Started multiline %s at %d\n", multilineMarker, multiLineStart);
	}

	/**
	 * Checks if current token is multiline marker, therefore multiline ended
	 * @return
	 */
	protected boolean isMultilineEnd()
	{
		return multilineMarker.equals(yytext().toString());
	}

	/**
	 * Ends multiline, pushback marker
	 * @return
	 */
	protected IElementType endMultiline()
	{
		setTokenStart(multiLineStart);
		yypushback(multilineMarker.length());
		yybegin_LEX_MULTILINE_TOKEN();

		return declaredMultiLineType;
	}

	// checking for built ins
	protected IElementType checkBuiltInScalar()
	{
		return PerlScalar.BUILT_IN.contains(yytext().toString())
				? PERL_BUILTIN_VARIABLE_SCALAR
				: PERL_VARIABLE_SCALAR;
	}
	protected IElementType checkBuiltInArray()
	{
		// here we need to check built-in scalars
		return PerlArray.BUILT_IN.contains(yytext().toString())
				? PERL_BUILTIN_VARIABLE_ARRAY
				: PERL_VARIABLE_ARRAY;
	}
	protected IElementType checkBuiltInHash()
	{
		// here we need to check built-in scalars
		return PerlHash.BUILT_IN.contains(yytext().toString())
				? PERL_BUILTIN_VARIABLE_HASH
				: PERL_VARIABLE_HASH;
	}
	protected IElementType checkBuiltInGlob()
	{
		// here we need to check built-in scalars
		return PerlGlob.BUILT_IN.contains(yytext().toString())
				? PERL_BUILTIN_VARIABLE_GLOB
				: PERL_VARIABLE_GLOB;
	}
	protected IElementType checkCorePackage()
	{
		return PERL_PACKAGE;
	}

}
