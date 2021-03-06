/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.weblite.netbeans.mirah.lexer;

import ca.weblite.netbeans.mirah.lexer.MirahParser.MirahParseDiagnostics.SyntaxError;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.Document;
import org.netbeans.api.java.classpath.ClassPath;

import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.ParserResultTask;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.LazyFixList;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author shannah
 */
class SyntaxErrorsHighlightingTask extends ParserResultTask {

    private static final Logger LOG = Logger.getLogger(SyntaxErrorsHighlightingTask.class.getCanonicalName());

    public SyntaxErrorsHighlightingTask() {
        //LOG.warning("In SyntaxErrorsHighlightingTask constructor");
    }

    @Override
    public void run(Parser.Result t, SchedulerEvent se) {
        MirahParser.NBMirahParserResult result = (MirahParser.NBMirahParserResult) t;
        List<SyntaxError> syntaxErrors = result.getDiagnostics().getErrors();
        Snapshot snapshot = result.getSnapshot();
        if ( snapshot == null ){
            return;
        }
        Source source = snapshot.getSource();
        if ( source == null ){
            return;
        }
        
        Document document = source.getDocument(false);
        if ( document == null ){
            return;
        }
        List<ErrorDescription> errors = new ArrayList<ErrorDescription>();
        for (SyntaxError syntaxError : syntaxErrors) {
            String message = syntaxError.message;
            int line =-1;
            try {
                //LOG.warning("About to parse line from ["+syntaxError.position+"]");
                String[] pieces = syntaxError.position.substring(0, syntaxError.position.lastIndexOf(":")).split(":");
                
                line = Integer.parseInt(pieces[pieces.length-1]);
                //LOG.warning("Parse error found on line "+line);
            } catch (NumberFormatException ex){
                ex.printStackTrace();
            }
            
            if (line <= 0) {
                continue;
            }
            ErrorDescription errorDescription = ErrorDescriptionFactory.createErrorDescription(
                    Severity.ERROR,
                    message,
                    document,
                    line);
            LOG.warning("Checking message");
            if ( message.toLowerCase().contains("cannot find class")){
                LOG.warning("Message cannot find class");
                List<Fix> imports = new ArrayList<Fix>();
                Pattern p = Pattern.compile("cannot find class ([a-zA-Z][a-zA-Z0-9\\.\\$]*)", Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(message);
                
                if ( m.find() ){
                    LOG.warning("It matches the pattern");
                    String className = m.group(1);
                    if ( className.indexOf(".") != -1 ){
                        int pos = className.lastIndexOf(".");
                        if ( pos < className.length()-1 ){
                            pos++;
                        }
                        className = className.substring(pos);
                    }
                    FileObject fo = source.getFileObject();
                    ClassPath[] classPaths = new ClassPath[]{
                        ClassPath.getClassPath(fo, ClassPath.SOURCE),
                        ClassPath.getClassPath(fo, ClassPath.EXECUTE),
                        ClassPath.getClassPath(fo, ClassPath.COMPILE),
                        ClassPath.getClassPath(fo, ClassPath.BOOT)
                    };
                    for ( ClassPath cp : classPaths){
                        List<ClassPath.Entry> entries = cp.entries();
                        for ( ClassPath.Entry entry : entries ){
                            LOG.warning("Entry "+entry);
                        }
                    }
                    
                }
            }
            
            errors.add(errorDescription);
        }
        HintsController.setErrors(document, "mirah", errors);
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public Class<? extends Scheduler> getSchedulerClass() {
        return Scheduler.EDITOR_SENSITIVE_TASK_SCHEDULER;
    }

    @Override
    public void cancel() {

    }
    
    
    
    

}
