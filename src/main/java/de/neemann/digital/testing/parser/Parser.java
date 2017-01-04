package de.neemann.digital.testing.parser;

import de.neemann.digital.lang.Lang;
import de.neemann.digital.testing.Value;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

/**
 * Parser to parse test data.
 * The constructor takes a string, and after a call to parse()
 * the names of the signals and the test vectors are generated.
 * Via the {@link #getValue()} or the {@link #getValue(Context)} functions you can utilize
 * the parser to evaluate integer functions.
 * If you want to evaluate an expression several times you should use the {@link #parseExpression()} function.
 * <p>
 * Created by Helmut.Neemann on 02.12.2016.
 */
public class Parser {

    private final ArrayList<String> names;
    private final ArrayList<Value[]> lines;
    private final ArrayList<Value> values;
    private final Tokenizer tok;

    /**
     * Creates a new instance
     *
     * @param data the test data string
     */
    public Parser(String data) {
        names = new ArrayList<>();
        lines = new ArrayList<>();
        values = new ArrayList<>();
        tok = new Tokenizer(new BufferedReader(new StringReader(data)));
    }

    /**
     * Parses the data
     *
     * @return this for chained calls
     * @throws IOException     IOException
     * @throws ParserException ParserException
     */
    public Parser parse() throws IOException, ParserException {
        parseHeader();
        parseValues();
        return this;
    }

    private void parseHeader() throws IOException, ParserException {
        tok.skipEmptyLines();
        while (true) {
            Tokenizer.Token token = tok.simpleIdent();
            switch (token) {
                case IDENT:
                    names.add(tok.getIdent());
                    break;
                case EOL:
                    return;
                default:
                    throw newUnexpectedToken(token);
            }
        }
    }

    private ParserException newUnexpectedToken(Tokenizer.Token token) {
        String name = token == Tokenizer.Token.IDENT ? tok.getIdent() : token.name();
        return new ParserException(Lang.get("err_unexpectedToken_N0_inLine_N1", name, tok.getLine()));
    }

    private void parseValues() throws IOException, ParserException {
        while (true) {
            Tokenizer.Token t = tok.peek();
            switch (t) {
                case EOL:
                    break;
                case EOF:
                    return;
                case NUMBER:
                    parseLine();
                    break;
                case IDENT:
                    if (tok.getIdent().equals("repeat")) {
                        tok.consume();
                        expect(Tokenizer.Token.OPEN);
                        int count = (int) parseInt();
                        expect(Tokenizer.Token.CLOSE);
                        parseForLine(count);
                    } else {
                        parseLine();
                    }
                    break;
                default:
                    throw newUnexpectedToken(t);
            }
        }
    }

    private void parseForLine(int count) throws IOException, ParserException {
        ArrayList<Entry> entries = new ArrayList<>();
        while (true) {
            Tokenizer.Token token = tok.next();
            switch (token) {
                case NUMBER:
                    Value num = new Value(tok.getIdent());
                    entries.add(n -> n.addValue(num));
                    break;
                case IDENT:
                    if (tok.getIdent().equals("bits")) {
                        expect(Tokenizer.Token.OPEN);
                        int bitCount = (int) parseInt();
                        expect(Tokenizer.Token.COMMA);
                        Expression exp = parseExpression();
                        entries.add(c -> c.addBits(bitCount, exp.value(c)));
                        expect(Tokenizer.Token.CLOSE);
                    } else {
                        Value v = new Value(tok.getIdent().toUpperCase());
                        entries.add(n -> n.addValue(v));
                    }
                    break;
                case OPEN:
                    Expression exp = parseExpression();
                    entries.add(c -> c.addValue(new Value((int) exp.value(c))));
                    expect(Tokenizer.Token.CLOSE);
                    break;
                case EOF:
                case EOL:
                    for (int n = 0; n < count; n++) {
                        for (Entry entry : entries) entry.calculate(new Context(values).setVar("n", n));
                        addLine();
                    }
                    return;
                default:
                    throw newUnexpectedToken(token);
            }
        }
    }

    private long parseInt() throws ParserException, IOException {
        return parseExpression().value(new Context());
    }

    private Tokenizer.Token parseLine() throws IOException, ParserException {
        while (true) {
            Tokenizer.Token token = tok.next();
            switch (token) {
                case IDENT:
                case NUMBER:
                    try {
                        values.add(new Value(tok.getIdent().toUpperCase()));
                    } catch (NumberFormatException e) {
                        throw new ParserException(Lang.get("err_notANumber_N0_inLine_N1", tok.getIdent(), tok.getLine()));
                    }
                    break;
                case EOF:
                case EOL:
                    addLine();
                    return token;
                default:
                    throw newUnexpectedToken(token);
            }
        }
    }

    private void addLine() throws ParserException {
        if (values.size() > 0) {

            if (values.size() != names.size())
                throw new ParserException(Lang.get("err_testDataExpected_N0_found_N1_numbersInLine_N2", names.size(), values.size(), tok.getLine()));

            lines.add(values.toArray(new Value[names.size()]));
            values.clear();
        }
    }

    private void expect(Tokenizer.Token token) throws IOException, ParserException {
        Tokenizer.Token t = tok.next();
        if (t != token)
            throw newUnexpectedToken(t);

    }

    /**
     * @return the used variables
     */
    public ArrayList<String> getNames() {
        return names;
    }

    /**
     * @return the test vectors
     */
    public ArrayList<Value[]> getLines() {
        return lines;
    }

    private interface Entry {
        void calculate(Context c) throws ParserException;
    }

    private boolean isToken(Tokenizer.Token t) throws IOException {
        if (tok.peek() == t) {
            tok.next();
            return true;
        }
        return false;
    }

    /**
     * Returns the value of the expression
     *
     * @return the value
     * @throws IOException     IOException
     * @throws ParserException ParserException
     */
    public long getValue() throws IOException, ParserException {
        return getValue(new Context());
    }

    /**
     * Returns the value of the expression
     *
     * @param context the context of the evaluation
     * @return the value
     * @throws IOException     IOException
     * @throws ParserException ParserException
     */
    public long getValue(Context context) throws IOException, ParserException {
        final long value = parseExpression().value(context);
        expect(Tokenizer.Token.EOF);
        return value;
    }

    /**
     * Parses a string to a simple expression
     *
     * @return the expression
     * @throws IOException     IOException
     * @throws ParserException IOException
     */
    public Expression parseExpression() throws IOException, ParserException {
        Expression ac = parseGreater();
        while (isToken(Tokenizer.Token.SMALER)) {
            Expression a = ac;
            Expression b = parseGreater();
            ac = (c) -> a.value(c) < b.value(c) ? 1 : 0;
        }
        return ac;
    }

    private Expression parseGreater() throws IOException, ParserException {
        Expression ac = parseEquals();
        while (isToken(Tokenizer.Token.GREATER)) {
            Expression a = ac;
            Expression b = parseEquals();
            ac = (c) -> a.value(c) > b.value(c) ? 1 : 0;
        }
        return ac;
    }

    private Expression parseEquals() throws IOException, ParserException {
        Expression ac = parseOR();
        while (isToken(Tokenizer.Token.EQUAL)) {
            Expression a = ac;
            Expression b = parseOR();
            ac = (c) -> a.value(c) == b.value(c) ? 1 : 0;
        }
        return ac;
    }

    private Expression parseOR() throws IOException, ParserException {
        Expression ac = parseAND();
        while (isToken(Tokenizer.Token.OR)) {
            Expression a = ac;
            Expression b = parseAND();
            ac = (c) -> a.value(c) | b.value(c);
        }
        return ac;
    }

    private Expression parseAND() throws IOException, ParserException {
        Expression ac = parseShiftRight();
        while (isToken(Tokenizer.Token.AND)) {
            Expression a = ac;
            Expression b = parseShiftRight();
            ac = (c) -> a.value(c) & b.value(c);
        }
        return ac;
    }

    private Expression parseShiftRight() throws IOException, ParserException {
        Expression ac = parseShiftLeft();
        while (isToken(Tokenizer.Token.SHIFTRIGHT)) {
            Expression a = ac;
            Expression b = parseShiftLeft();
            ac = (c) -> a.value(c) >> b.value(c);
        }
        return ac;
    }

    private Expression parseShiftLeft() throws IOException, ParserException {
        Expression ac = parseAdd();
        while (isToken(Tokenizer.Token.SHIFTLEFT)) {
            Expression a = ac;
            Expression b = parseAdd();
            ac = (c) -> a.value(c) << b.value(c);
        }
        return ac;
    }

    private Expression parseAdd() throws IOException, ParserException {
        Expression ac = parseSub();
        while (isToken(Tokenizer.Token.ADD)) {
            Expression a = ac;
            Expression b = parseSub();
            ac = (c) -> a.value(c) + b.value(c);
        }
        return ac;
    }

    private Expression parseSub() throws IOException, ParserException {
        Expression ac = parseMul();
        while (isToken(Tokenizer.Token.SUB)) {
            Expression a = ac;
            Expression b = parseMul();
            ac = (c) -> a.value(c) - b.value(c);
        }
        return ac;
    }

    private Expression parseMul() throws IOException, ParserException {
        Expression ac = parseDiv();
        while (isToken(Tokenizer.Token.MUL)) {
            Expression a = ac;
            Expression b = parseDiv();
            ac = (c) -> a.value(c) * b.value(c);
        }
        return ac;
    }

    private Expression parseDiv() throws IOException, ParserException {
        Expression ac = parseIdent();
        while (isToken(Tokenizer.Token.DIV)) {
            Expression a = ac;
            Expression b = parseIdent();
            ac = (c) -> a.value(c) / b.value(c);
        }
        return ac;
    }

    private Expression parseIdent() throws IOException, ParserException {
        Tokenizer.Token t = tok.next();
        switch (t) {
            case IDENT:
                String name = tok.getIdent();
                return (c) -> c.getVar(name);
            case NUMBER:
                try {
                    long num = Long.decode(tok.getIdent());
                    return (c) -> num;
                } catch (NumberFormatException e) {
                    throw new ParserException(Lang.get("err_notANumber_N0_inLine_N1", tok.getIdent(), tok.getLine()));
                }
            case SUB:
                Expression negExp = parseIdent();
                return (c) -> -negExp.value(c);
            case NOT:
                Expression notExp = parseIdent();
                return (c) -> ~notExp.value(c);
            case OPEN:
                Expression exp = parseExpression();
                expect(Tokenizer.Token.CLOSE);
                return exp;
            default:
                throw newUnexpectedToken(t);
        }
    }
}
