/*
 * Copyright (c) 2018 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.core.extern;

import de.neemann.digital.core.element.ElementAttributes;
import de.neemann.digital.core.element.Keys;
import de.neemann.digital.core.extern.VerilogTokenizer.Token;
import de.neemann.digital.hdl.hgs.Context;
import de.neemann.digital.hdl.hgs.HGSEvalException;
import de.neemann.digital.hdl.hgs.Parser;
import de.neemann.digital.hdl.hgs.Statement;

import java.io.*;
import java.nio.file.Files;
import java.util.NoSuchElementException;


/**
 * Base class of applications which are able to interpret Verilog-Code.
 * The generated verilog code is able to operate with the {@link de.neemann.digital.core.extern.handler.StdIOInterface}.
 */
public abstract class ApplicationVerilogStdIO implements Application {
    private Token currToken;

    private static final Statement TEMPLATE =
            Parser.createFromJarStatic("verilog/VerilogStdIOTemplate.vtpl");

    /**
     * Creates a verilog file in a temp directory.
     *
     * @param label   the name of the verilog code
     * @param code    the verilog code
     * @param inputs  the inputs
     * @param outputs the outputs
     * @param root    the projects main folder
     * @return the verilog file
     * @throws IOException IOException
     */
    public File createVerilogFile(String label, String code, PortDefinition inputs, PortDefinition outputs, File root) throws IOException {
        File dir = Files.createTempDirectory("digital_verilog_").toFile();

        File file = new File(dir, label + ".v");

        try (Writer w = new FileWriter(file)) {
            w.write(createVerilog(label, code, inputs, outputs, root));
        } catch (HGSEvalException e) {
            throw new IOException("error evaluating the template", e);
        }

        return file;
    }

    /**
     * Creates the verilog code
     *
     * @param label   the name of the verilog module
     * @param code    the verilog code
     * @param inputs  the inputs
     * @param outputs the outputs
     * @param parameters the parameters
     * @param root    the projects main folder
     * @return the verilog code
     * @throws HGSEvalException HGSEvalException
     */
    public String createVerilog(String label, String code, PortDefinition inputs, PortDefinition outputs, File root) throws HGSEvalException {
        Context context = new Context(root)
                .declareVar("moduleName", label)
                .declareVar("code", code)
                .declareVar("inputs", inputs)
                .declareVar("outputs", outputs);

        TEMPLATE.execute(context);
        return context.toString();
    }

    private void match(Token tkExpect, String tkText, VerilogTokenizer st) throws ParseException, IOException, VerilogTokenizer.TokenizerException {
        if (currToken != tkExpect) {
            throw new ParseException("unexpected '" + tkText + "'");
        }
        currToken = st.nextToken();
    }

    private boolean match0(Token tkExpect, String tkText, VerilogTokenizer st) throws ParseException, IOException, VerilogTokenizer.TokenizerException {
        if (currToken != tkExpect) {
            return false;
        }
        currToken = st.nextToken();
        return true;   
    }

    @Override
    public boolean ensureConsistency(ElementAttributes attributes, File root) {
        try {
            String code = Application.getCode(attributes, root);
            VerilogTokenizer st = new VerilogTokenizer(new StringReader(code));

            PortDefinition in;
            PortDefinition out;
            ParamDefinition par;
            String label;

            currToken = st.nextToken();

            match(Token.MODULE, "keyword 'module'", st);
            label = st.value();
            match(Token.IDENT, "identifier", st);

            par = new ParamDefinition("");

            if (match0(Token.HASH, "'#'", st)) {
                match(Token.OPENPAR, "'('", st);
                scanParamArgs(st, par);
            }

            attributes.set(Keys.EXTERNAL_PARAMETERS, par.toString());
        
            match(Token.OPENPAR, "'('", st);

            in = new PortDefinition("");
            out = new PortDefinition("");
            scanPortArgs(st, in, out, par);

            if (currToken == Token.SEMICOLON) {
                if (in.size() == 0 && out.size() == 0) {
                    do {
                        currToken = st.nextToken();
                        if (currToken == Token.INPUT || currToken == Token.OUTPUT)
                            scanPort(st, in, out, par);
                    } while ((currToken != Token.ENDMODULE) && (currToken != Token.EOF));
                }
            } else {
                return false;
            }

            if (in.size() > 0 && out.size() > 0) {
                attributes.set(Keys.LABEL, label);
                attributes.set(Keys.EXTERNAL_INPUTS, in.toString());
                attributes.set(Keys.EXTERNAL_OUTPUTS, out.toString());
                return true;
            } else
                return false;

        } catch (NoSuchElementException | ParseException | VerilogTokenizer.TokenizerException | IOException e) {
            return false;
        }
    }

    private void scanParamArgs(VerilogTokenizer st, ParamDefinition par) throws ParseException, IOException, VerilogTokenizer.TokenizerException {
        while (true) {
            switch (currToken) {
                case IDENT:
                    currToken = st.nextToken();
                    break;
                case EQ:
                    currToken = st.nextToken();
                    break;
                case NUMBER:
                    currToken = st.nextToken();
                    break;
                case PARAMETER:
                    scanParam(st, par);
                    currToken = st.nextToken();
                    break;
                case CLOSEPAR:
                    currToken = st.nextToken();
                    return;
                case COMMA:
                    currToken = st.nextToken();
                    break;
                default:
                    throw new ParseException("unexpected '" + st.value() + "'");
            }
        }
    }

    private void scanParam(VerilogTokenizer st, ParamDefinition par) throws ParseException, IOException, VerilogTokenizer.TokenizerException {

        if (currToken == Token.PARAMETER) {
            currToken = st.nextToken();
        }
        int bits = 32;
        if (currToken == Token.OPENBRACKET) {
            match(Token.OPENBRACKET, "", st);
            String rangeStart = st.value();
            match(Token.NUMBER, "a number", st);
            match(Token.COLON, "':'", st);
            String rangeEnd = st.value();
            match(Token.NUMBER, "a number", st);
            match(Token.CLOSEBRACKET, "']'", st);
            bits = (Integer.parseInt(rangeStart) - Integer.parseInt(rangeEnd)) + 1;
        }
        String name = st.value();
        match(Token.IDENT, "identifier", st);
        
        match(Token.EQ, "'='", st);
        //match(Token.NUMBER, "a number", st);
        int val = Integer.parseInt(st.value());
        par.addParam(name, bits, val);
    }

    private void scanPortArgs(VerilogTokenizer st, PortDefinition in, PortDefinition out, ParamDefinition par) throws ParseException, IOException, VerilogTokenizer.TokenizerException {
        while (true) {
            switch (currToken) {
                case IDENT:
                    currToken = st.nextToken();
                    break;
                case INPUT:
                case OUTPUT:
                    scanPort(st, in, out, par);
                    break;
                case CLOSEPAR:
                    currToken = st.nextToken();
                    return;
                case COMMA:
                    currToken = st.nextToken();
                    break;
                default:
                    throw new ParseException("unexpected '" + st.value() + "'");
            }
        }
    }

    private void scanPort(VerilogTokenizer st, PortDefinition in, PortDefinition out, ParamDefinition par) throws ParseException, IOException, VerilogTokenizer.TokenizerException {
        boolean isInput;

        switch (currToken) {
            case INPUT:
                isInput = true;
                currToken = st.nextToken();
                if (currToken == Token.WIRE
                    || currToken == Token.LOGIC) {
                    currToken = st.nextToken();
                }
                break;
            case OUTPUT:
                isInput = false;
                currToken = st.nextToken();
                if (currToken == Token.WIRE
                        || currToken == Token.REG
                        || currToken == Token.LOGIC) {
                    currToken = st.nextToken();
                }
                break;
            default:
                throw new ParseException("unexpected '" + st.value() + "'");
        }

        int bits = 1;
        if (currToken == Token.OPENBRACKET) {
            match(Token.OPENBRACKET, "", st);

            int rangeStart = 0;
            int rangeEnd = 0;
            boolean isPlus = false;
            boolean isMinus = false;

            do {
                switch (currToken) {
                    case IDENT:
                        String namepar = st.value();
                        if (isPlus) {
                            rangeStart += par.getParamVal(namepar);
                            isPlus = false;
                        }
                        else if (isMinus) {
                            rangeStart -= par.getParamVal(namepar);
                            isMinus = false;
                        }
                        else rangeStart = par.getParamVal(namepar);
                        currToken = st.nextToken();
                        break;
                    case PLUS: 
                        isPlus = true;
                        currToken = st.nextToken();
                        break; 
                    case MINUS:
                        isMinus = true;
                        currToken = st.nextToken();
                        break;
                    case NUMBER:
                        if (isPlus) {
                            rangeStart += Integer.parseInt(st.value());
                            isPlus = false;
                        }
                        else if (isMinus) {
                            rangeStart -= Integer.parseInt(st.value());
                            isMinus = false;
                        }
                        else rangeStart = Integer.parseInt(st.value());
                        currToken = st.nextToken();
                        break;                        
                    default: 
                        break;
                }
            } while (currToken != Token.COLON);      

            match(Token.COLON, "':'", st);
            
            do {
                switch (currToken) {
                    case IDENT:
                        String namepar = st.value();
                        if (isPlus) {
                            rangeEnd += par.getParamVal(namepar);
                            isPlus = false;
                        }
                        else if (isMinus) {
                            rangeEnd -= par.getParamVal(namepar);
                            isMinus = false;
                        }
                        else rangeEnd = par.getParamVal(namepar);
                        currToken = st.nextToken();
                        break;
                    case PLUS: 
                        isPlus = true;
                        currToken = st.nextToken();
                        break; 
                    case MINUS:
                        isMinus = true;
                        currToken = st.nextToken();
                        break;
                    case NUMBER:
                        if (isPlus) {
                            rangeEnd += Integer.parseInt(st.value());
                            isPlus = false;
                        }
                        else if (isMinus) {
                            rangeEnd -= Integer.parseInt(st.value());
                            isMinus = false;
                        }
                        else rangeEnd = Integer.parseInt(st.value());
                        currToken = st.nextToken();
                        break;                        
                    default: 
                        break;
                }
            } while (currToken != Token.CLOSEBRACKET);  
            
            match(Token.CLOSEBRACKET, "']'", st);

            bits = rangeStart - rangeEnd + 1;
        }

        String name = st.value();
        match(Token.IDENT, "identifier", st);

        if (isInput) {
            in.addPort(name, bits);
        } else {
            out.addPort(name, bits);
        }

        while (currToken == Token.COMMA) {
            match(Token.COMMA, "comma", st);
            if (currToken != Token.IDENT)
                return;
            name = st.value();
            match(Token.IDENT, "identifier", st);

            if (isInput) {
                in.addPort(name, bits);
            } else {
                out.addPort(name, bits);
            }
        }
    }

    private static final class ParseException extends Exception {
        private ParseException(String message) {
            super(message);
        }
    }

}
