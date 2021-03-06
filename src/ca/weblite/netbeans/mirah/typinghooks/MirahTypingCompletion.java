/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.weblite.netbeans.mirah.typinghooks;

import ca.weblite.netbeans.mirah.lexer.MirahLanguageHierarchy;
import ca.weblite.netbeans.mirah.lexer.MirahTokenId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import mirah.impl.Tokens;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.lexer.PartType;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;
import org.netbeans.spi.editor.typinghooks.DeletedTextInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedBreakInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor;

/**
 *
 * @author shannah
 */
public class MirahTypingCompletion {
   
    private static final Logger LOG = Logger.getLogger(MirahTypingCompletion.class.getCanonicalName());
    /**
     * Returns true if bracket completion is enabled in options.
     */
    static boolean isCompletionSettingEnabled() {
        //Preferences prefs = MimeLookup.getLookup(JavaKit.JAVA_MIME_TYPE).lookup(Preferences.class);
        //return prefs.getBoolean(SimpleValueNames.COMPLETION_PAIR_CHARACTERS, false);
        return true;
    }
    
    private static int tokenBalance(Document doc, MirahTokenId leftTokenId) {
        TokenBalance tb = TokenBalance.get(doc);
        
        tb.addTokenPair(MirahTokenId.getLanguage(), MirahTokenId.get(Tokens.tLParen.ordinal()), MirahTokenId.get(Tokens.tRParen.ordinal()));
        tb.addTokenPair(MirahTokenId.getLanguage(), MirahTokenId.get(Tokens.tLBrace.ordinal()), MirahTokenId.get(Tokens.tRBrace.ordinal()));
        tb.addTokenPair(MirahTokenId.getLanguage(), MirahTokenId.get(Tokens.tLBrack.ordinal()), MirahTokenId.get(Tokens.tRBrack.ordinal()));
        //tb.addTokenPair(MirahTokenId.getLanguage(), MirahTokenId.get(Tokens.tDo.ordinal()), MirahTokenId.get(Tokens.tEnd.ordinal()));
        
        
        int balance = tb.balance(MirahTokenId.getLanguage(), leftTokenId);
        assert (balance != Integer.MAX_VALUE);
        return balance;
    }
    
    /**
     * Returns position of the first unpaired closing paren/brace/bracket from the caretOffset
     * till the end of caret row. If there is no such element, position after the last non-white
     * character on the caret row is returned.
     */
    static int getRowOrBlockEnd(BaseDocument doc, int caretOffset, boolean[] insert) throws BadLocationException {
        int rowEnd = org.netbeans.editor.Utilities.getRowLastNonWhite(doc, caretOffset);
        if (rowEnd == -1 || caretOffset >= rowEnd) {
            return caretOffset;
        }
        rowEnd += 1;
        int parenBalance = 0;
        int braceBalance = 0;
        int bracketBalance = 0;
        TokenSequence<MirahTokenId> ts = mirahTokenSequence(doc, caretOffset, false);
        if (ts == null) {
            return caretOffset;
        }
        
        int lParen = 999;
        
        while (ts.offset() < rowEnd) {
            final int id = ts.token().id().ordinal();
            if ( id == Tokens.tLParen.ordinal()) {
                parenBalance++;
            } else if ( id == Tokens.tRParen.ordinal()){
                parenBalance--;
            } else if ( id == Tokens.tLBrace.ordinal()){
                braceBalance++;
            } else if ( id == Tokens.tRBrace.ordinal()){
                braceBalance--;
            } else if ( id == Tokens.tLBrack.ordinal()){
                bracketBalance++;
            } else if ( id == Tokens.tRBrack.ordinal()){
                bracketBalance--;
            }
            
            if (!ts.moveNext()) {
                break;
            }
        }

        insert[0] = false;
        return rowEnd;
    }
    
    private static TokenSequence<MirahTokenId> mirahTokenSequence(TypedTextInterceptor.MutableContext context, boolean backwardBias) {
        return mirahTokenSequence(context.getDocument(), context.getOffset(), backwardBias);
    }

    private static TokenSequence<MirahTokenId> mirahTokenSequence(DeletedTextInterceptor.Context context, boolean backwardBias) {
        return mirahTokenSequence(context.getDocument(), context.getOffset(), backwardBias);
    }
    
       private static TokenSequence<MirahTokenId> mirahTokenSequence(TypedBreakInterceptor.Context context, boolean backwardBias) {
        return mirahTokenSequence(context.getDocument(), context.getCaretOffset(), backwardBias);
    }

    /**
     * Get token sequence positioned over a token.
     *
     * @param doc
     * @param caretOffset
     * @param backwardBias
     * @return token sequence positioned over a token that "contains" the offset
     * or null if the document does not contain any java token sequence or the
     * offset is at doc-or-section-start-and-bwd-bias or
     * doc-or-section-end-and-fwd-bias.
     */
    private static TokenSequence<MirahTokenId> mirahTokenSequence(Document doc, int caretOffset, boolean backwardBias) {
        TokenHierarchy<?> hi = TokenHierarchy.get(doc);
        List<TokenSequence<?>> tsList = hi.embeddedTokenSequences(caretOffset, backwardBias);
        // Go from inner to outer TSes
        for (int i = tsList.size() - 1; i >= 0; i--) {
            TokenSequence<?> ts = tsList.get(i);
            if (ts.languagePath().innerLanguage() == MirahTokenId.getLanguage()) {
                TokenSequence<MirahTokenId> javaInnerTS = (TokenSequence<MirahTokenId>) ts;
                return javaInnerTS;
            }
        }
        return null;
    }
    
    /**
     * Resolve whether pairing right curly should be added automatically
     * at the caret position or not.
     * <br>
     * There must be only whitespace or line comment or block comment
     * between the caret position
     * and the left brace and the left brace must be on the same line
     * where the caret is located.
     * <br>
     * The caret must not be "contained" in the opened block comment token.
     *
     * @param doc document in which to operate.
     * @param caretOffset offset of the caret.
     * @return true if a right brace '}' should be added
     *  or false if not.
     */
    static boolean isAddRightBrace(BaseDocument doc, int caretOffset) throws BadLocationException {
        if (tokenBalance(doc, MirahTokenId.get(Tokens.tLBrace.ordinal())) <= 0) {
            return false;
        }
        int caretRowStartOffset = org.netbeans.editor.Utilities.getRowStart(doc, caretOffset);
        TokenSequence<MirahTokenId> ts = mirahTokenSequence(doc, caretOffset, true);
        if (ts == null) {
            return false;
        }
        boolean first = true;
        
        MirahTokenId WHITESPACE = MirahTokenId.get(999);
        //MirahTokenId LINE_COMMENT = MirahTokenId.get(Tokens.t)
        MirahTokenId LBRACE = MirahTokenId.get(Tokens.tLBrace.ordinal());
        
        do {
            if (ts.offset() < caretRowStartOffset) {
                return false;
            }
            
            if ( ts.token().id().equals(LBRACE)){
                return true;
            }
            
            
            first = false;
        } while (ts.movePrevious());
        return false;
    }
    
    /**
     * Check for various conditions and possibly add a pairing bracket.
     *
     * @param context
     * @throws BadLocationException
     */
    static void completeOpeningBracket(TypedTextInterceptor.MutableContext context) throws BadLocationException {
        if (isStringOrComment(mirahTokenSequence(context, false).token().id())) {
            return;
        }
        char chr = context.getDocument().getText(context.getOffset(), 1).charAt(0);
        if (chr == ')' || chr == ',' || chr == '\"' || chr == '\'' || chr == ' ' || chr == ']' || chr == '}' || chr == '\n' || chr == '\t' || chr == ';') {
            char insChr = context.getText().charAt(0);
            context.setText("" + insChr + matching(insChr) , 1);  // NOI18N
        }
    }
    
    
    /**
     * Returns for an opening bracket or quote the appropriate closing
     * character.
     */
    private static char matching(char bracket) {
        switch (bracket) {
            case '(':
                return ')';
            case '[':
                return ']';
            case '\"':
                return '\"'; // NOI18N
            case '\'':
                return '\'';
            default:
                return ' ';
        }
    }
    
      private static MirahTokenId.Enum matching(MirahTokenId.Enum id) {
        switch (id) {
            case LPAREN:
                return MirahTokenId.Enum.RPAREN;
            case LBRACK:
                return MirahTokenId.Enum.RBRACK;
            case RPAREN:
                return MirahTokenId.Enum.LPAREN;
            case RBRACK:
                return MirahTokenId.Enum.LBRACK;
            default:
                return null;
        }
    }
    
    private static boolean isStringOrComment(MirahTokenId id) {
        return id.ordinal() == Tokens.tStringContent.ordinal();
    }
    
    /**
     * Check for various conditions and possibly skip a closing bracket.
     *
     * @param context
     * @return relative caretOffset change
     * @throws BadLocationException
     */
    static int skipClosingBracket(TypedTextInterceptor.MutableContext context) throws BadLocationException {
        TokenSequence<MirahTokenId> javaTS = mirahTokenSequence(context, false);
        if (javaTS == null || (javaTS.token().id().ordinal() != Tokens.tRParen.ordinal()) && javaTS.token().id().ordinal() != Tokens.tRBrack.ordinal() || isStringOrComment(javaTS.token().id())) {
            return -1;
        }

        MirahTokenId.Enum bracketId = bracketCharToId(context.getText().charAt(0));
        if (isSkipClosingBracket(context, javaTS, bracketId)) {
            context.setText("", 0);  // NOI18N
            return context.getOffset() + 1;
        }
        return -1;
    }
    
    private static MirahTokenId.Enum bracketCharToId(char bracket) {
        switch (bracket) {
            case '(':
                return MirahTokenId.Enum.LPAREN;
            case ')':
                return MirahTokenId.Enum.RPAREN;
            case '[':
                return MirahTokenId.Enum.LBRACK;
            case ']':
                return MirahTokenId.Enum.RBRACK;
            case '{':
                return MirahTokenId.Enum.LBRACE;
            case '}':
                return MirahTokenId.Enum.RBRACE;
            default:
                throw new IllegalArgumentException("Not a bracket char '" + bracket + '\'');  // NOI18N
        }
    }
    
    private static Set<MirahTokenId> STOP_TOKENS_FOR_SKIP_CLOSING_BRACKET = new HashSet<MirahTokenId>();
    static {
        STOP_TOKENS_FOR_SKIP_CLOSING_BRACKET.add(MirahTokenId.LBRACE);
        STOP_TOKENS_FOR_SKIP_CLOSING_BRACKET.add(MirahTokenId.RBRACE);
    }

    
    
    private static boolean isSkipClosingBracket(TypedTextInterceptor.MutableContext context, TokenSequence<MirahTokenId> javaTS, MirahTokenId.Enum rightBracketId) {
        LOG.warning("isSkipClosingBracket "+rightBracketId+", "+context.getOffset()+", "+context.getDocument().getLength());
        if (context.getOffset() == context.getDocument().getLength()) {
            return false;
        }

        boolean skipClosingBracket = false;

        if (javaTS != null && javaTS.token().id().asEnum() == rightBracketId) {
            MirahTokenId.Enum leftBracketId = matching(rightBracketId);
            // Skip all the brackets of the same type that follow the last one
            do {
                if (STOP_TOKENS_FOR_SKIP_CLOSING_BRACKET.contains(javaTS.token().id())
                        || (javaTS.token().id() == MirahTokenId.WHITESPACE && javaTS.token().text().toString().contains("\n"))) {  // NOI18N
                    while (javaTS.token().id().asEnum() != rightBracketId) {
                        boolean isPrevious = javaTS.movePrevious();
                        if (!isPrevious) {
                            break;
                        }
                    }
                    break;
                }
            } while (javaTS.moveNext());

            // token var points to the last bracket in a group of two or more right brackets
            // Attempt to find the left matching bracket for it
            // Search would stop on an extra opening left brace if found
            int braceBalance = 0; // balance of '{' and '}'
            int bracketBalance = -1; // balance of the brackets or parenthesis
            int numOfSemi = 0;
            boolean finished = false;
            while (!finished && javaTS.movePrevious()) {
                MirahTokenId tokId = javaTS.token().id();
                if ( tokId == null ){
                    continue;
                }
                MirahTokenId.Enum id = tokId.asEnum();
                if ( id == null ){
                    continue;
                }
                switch (id) {
                    case LPAREN:
                    case LBRACK:
                        if (id == leftBracketId) {
                            bracketBalance++;
                            if (bracketBalance == 1) {
                                if (braceBalance != 0) {
                                    // Here the bracket is matched but it is located
                                    // inside an unclosed brace block
                                    // e.g. ... ->( } a()|)
                                    // which is in fact illegal but it's a question
                                    // of what's best to do in this case.
                                    // We chose to leave the typed bracket
                                    // by setting bracketBalance to 1.
                                    // It can be revised in the future.
                                    bracketBalance = 2;
                                }
                                finished = javaTS.offset() < context.getOffset();
                            }
                        }
                        break;

                    case RPAREN:
                    case RBRACK:
                        if (id == rightBracketId) {
                            bracketBalance--;
                        }
                        break;

                    case LBRACE:
                        braceBalance++;
                        if (braceBalance > 0) { // stop on extra left brace
                            finished = true;
                        }
                        break;

                    case RBRACE:
                        braceBalance--;
                        break;

                    //case SEMICOLON:
                    //    numOfSemi++;
                    //    break;
                }
            }

            if (bracketBalance == 1 && numOfSemi < 2) {
                finished = false;
                while (!finished && javaTS.movePrevious()) {
                    switch (javaTS.token().id().asEnum()) {
                        case WHITESPACE:
                        //case LINE_COMMENT:
                        //case BLOCK_COMMENT:
                        //case JAVADOC_COMMENT:
                            break;
                        //case FOR:
                        //    bracketBalance--;
                        default:
                            finished = true;
                            break;
                    }
                }
            }

            LOG.warning("BRACKET BALANCE "+bracketBalance);
            skipClosingBracket = bracketBalance != 1;
        }
        return skipClosingBracket;

    }
    
    /**
     * Called to insert either single bracket or bracket pair. 
     *
     * @param context
     * @return relative caretOffset change
     * @throws BadLocationException
     */
    static int completeQuote(TypedTextInterceptor.MutableContext context) throws BadLocationException {
        if (isEscapeSequence(context)) {
            return -1;
        }
        // Examine token id at the caret offset
        TokenSequence<MirahTokenId> javaTS = mirahTokenSequence(context, true);
        MirahTokenId.Enum id = (javaTS != null) ? javaTS.token().id().asEnum() : null;


        // If caret within comment return false
        if ( id != null ){
            LOG.warning("INSIDE TOKEN: "+javaTS.offset()+", "+javaTS.token().length()+", "+context.getOffset());
        }
        boolean caretInsideToken = (id != null)
                && (javaTS.offset() + javaTS.token().length() >= context.getOffset()
                || javaTS.token().partType() == PartType.START);
        //if (caretInsideToken && (id == JavaTokenId.BLOCK_COMMENT || id == JavaTokenId.JAVADOC_COMMENT || id == JavaTokenId.LINE_COMMENT)) {
        //    return -1;
       // }

        boolean completablePosition = isQuoteCompletablePosition(context);
        boolean insideString = caretInsideToken
                && (id == MirahTokenId.Enum.STRING_LITERAL || id == MirahTokenId.Enum.CHAR_LITERAL || id == MirahTokenId.Enum.SQUOTE || id == MirahTokenId.Enum.DQUOTE);
        
        int lastNonWhite = org.netbeans.editor.Utilities.getRowLastNonWhite((BaseDocument) context.getDocument(), context.getOffset());
        // eol - true if the caret is at the end of line (ignoring whitespaces)
        boolean eol = lastNonWhite < context.getOffset();
        LOG.warning("COMPLETE QUOTE: "+insideString+ " "+id+" ");
        if (insideString) {
            if (eol) {
                return -1;
            } else {
                //#69524
                char chr = context.getDocument().getText(context.getOffset(), 1).charAt(0);
                if (chr == context.getText().charAt(0)) {
                    //#83044
                    if (context.getOffset() > 0) {
                        javaTS.move(context.getOffset() - 1);
                        if (javaTS.moveNext()) {
                            id = javaTS.token().id().asEnum();
                            if (id == MirahTokenId.Enum.STRING_LITERAL || id == MirahTokenId.Enum.CHAR_LITERAL || (chr == '\'' && id == MirahTokenId.Enum.SQUOTE) || (chr == '"' && id == MirahTokenId.Enum.DQUOTE) ) {
                                context.setText("", 0); // NOI18N
                                return context.getOffset() + 1;
                            }
                        }
                    }
                }
            }
        }

        if ((completablePosition && !insideString) || eol) {
            context.setText(context.getText() + context.getText(), 1);
        }
        return -1;
    }
    
    private static boolean isEscapeSequence(TypedTextInterceptor.MutableContext context) throws BadLocationException {
        if (context.getOffset() <= 0) {
            return false;
        }

        char[] previousChars;
        for (int i = 2; context.getOffset() - i >= 0; i += 2) {
            previousChars = context.getDocument().getText(context.getOffset() - i, 2).toCharArray();
            if (previousChars[1] != '\\') {
                return false;
            }
            if (previousChars[0] != '\\') {
                return true;
            }
        }
        return context.getDocument().getText(context.getOffset() - 1, 1).charAt(0) == '\\';
    }
    
    private static boolean isQuoteCompletablePosition(TypedTextInterceptor.MutableContext context) throws BadLocationException {
        if (context.getOffset() == context.getDocument().getLength()) {
            return true;
        } else {
            for (int i = context.getOffset(); i < context.getDocument().getLength(); i++) {
                char chr = context.getDocument().getText(i, 1).charAt(0);
                if (chr == '\n') {
                    break;
                }
                if (!Character.isWhitespace(chr)) {
                    return (chr == ')' || chr == ',' || chr == '+' || chr == '}' || chr == ';');
                }

            }
            return false;
        }
    }
    
    
    /**
     * Check for various conditions and possibly remove two brackets.
     *
     * @param context
     * @throws BadLocationException
     */
    static void removeBrackets(DeletedTextInterceptor.Context context) throws BadLocationException {
        int caretOffset = context.isBackwardDelete() ? context.getOffset() - 1 : context.getOffset();
        TokenSequence<MirahTokenId> ts = mirahTokenSequence(context.getDocument(), caretOffset, false);
        if (ts == null) {
            return;
        }

        switch (ts.token().id().asEnum()) {
            case RPAREN:
                if (tokenBalance(context.getDocument(), MirahTokenId.LPAREN) != 0) {
                    context.getDocument().remove(caretOffset, 1);
                }
                break;
            case RBRACK:
                if (tokenBalance(context.getDocument(), MirahTokenId.LBRACK) != 0) {
                    context.getDocument().remove(caretOffset, 1);
                }
                break;
        }
    }
    
    /**
     * Check for various conditions and possibly remove two quotes.
     *
     * @param context
     * @throws BadLocationException
     */
    static void removeCompletedQuote(DeletedTextInterceptor.Context context) throws BadLocationException {
        TokenSequence<MirahTokenId> ts = mirahTokenSequence(context, false);
        if (ts == null) {
            return;
        }
        char removedChar = context.getText().charAt(0);
        int caretOffset = context.isBackwardDelete() ? context.getOffset() - 1 : context.getOffset();
        if (removedChar == '\"' || removedChar == '\'') {
            if (ts.token().id().asEnum() == MirahTokenId.Enum.STRING_LITERAL && ts.offset() == caretOffset) {
                context.getDocument().remove(caretOffset, 1);
            }
        } else if (removedChar == '\'') {
            if (ts.token().id().asEnum() == MirahTokenId.Enum.CHAR_LITERAL && ts.offset() == caretOffset) {
                context.getDocument().remove(caretOffset, 1);
            }
        }
    }

}
