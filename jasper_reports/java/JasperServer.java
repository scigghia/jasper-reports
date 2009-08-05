import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.webserver.WebServer;
//import org.apache.xmlrpc.webserver.*;
import org.apache.xmlrpc.*;
import org.apache.xmlrpc.server.PropertyHandlerMapping;

//import org.apache.xml.security.utils.Base64;

import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.JasperFillManager; 
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.data.JRXmlDataSource;
import net.sf.jasperreports.engine.data.JRCsvDataSource;
import net.sf.jasperreports.engine.JREmptyDataSource;

// Exporters
import net.sf.jasperreports.engine.JRAbstractExporter;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.JRRtfExporter;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.export.JRTextExporter;
import net.sf.jasperreports.engine.export.JRHtmlExporter;
import net.sf.jasperreports.engine.export.JRHtmlExporterParameter;
import net.sf.jasperreports.engine.export.oasis.JROdtExporter;
import net.sf.jasperreports.engine.export.oasis.JROdsExporter;

import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.io.ByteArrayInputStream;
import java.io.*;
import java.sql.*;
import java.lang.Class;
import java.math.BigDecimal;
import java.io.InputStream;
import java.util.Locale;


public class JasperServer { 

    /* Compiles the given .jrxml (inputFile) into a .jasper (outputFile) */
    public Boolean compile( String inputFile, String outputFile) {
        try{
            JasperCompileManager.compileReportToFile( inputFile, outputFile );
        } catch( Exception e ){
            return false;
        }
        return true;
    }

    public Boolean execute( Hashtable connectionParameters, String jrxmlPath, String outputPath, Hashtable parameters) throws java.lang.Exception {

        JasperReport report = null;
        byte[] result = null;
        JasperPrint jasperPrint = null;
        InputStream in = null;
        String jasperPath;
        File jrxmlFile;
        File jasperFile;
        int index;

        index = jrxmlPath.lastIndexOf('.');
        if ( index != -1 )
            jasperPath = jrxmlPath.substring( 0, index ) + ".jasper";
        else
            jasperPath = jrxmlPath + ".jasper";
        System.out.println( "JASPER FILE: " + jasperPath );

        jrxmlFile = new File( jrxmlPath );
        jasperFile = new File( jasperPath );
        if ( ! jasperFile.exists() || jrxmlFile.lastModified() > jasperFile.lastModified() ) {
            System.out.println( "Before compiling..") ;
            JasperCompileManager.compileReportToFile( jrxmlPath, jasperPath );
            System.out.println( "compiled...!");
        }
            
        System.out.println( parameters );

        report = (JasperReport) JRLoader.loadObject( jasperPath );

        // Add SUBREPORT_DIR parameter
        index = jrxmlPath.lastIndexOf('/');
        if ( index != -1 )
            parameters.put( "SUBREPORT_DIR", jrxmlPath.substring( 0, index+1 ) );

        // Fill in report parameters
        JRParameter[] reportParameters = report.getParameters();
        System.out.println( "Parameters.length:"+ reportParameters.length );
        for( int j=0; j < reportParameters.length; j++ ){
            System.out.println( "EVAL: " + j );
            //try{
            JRParameter jparam = reportParameters[j];    
            System.out.println( " PARAM:"+ jparam.getName() + " " + jparam.getValueClassName() );
            if ( jparam.getValueClassName().equals( "java.util.Locale" ) ) {
                // REPORT_LOCALE
                if ( ! parameters.containsKey( jparam.getName() ) )
                    continue;
                String[] locales = ((String)parameters.get( jparam.getName() )).split( "_" );
                Locale locale;
                
                if ( locales.length == 1 )
                    locale = new Locale( locales[0] );
                else
                    locale = new Locale( locales[0], locales[1] );
                parameters.put( jparam.getName(), locale );

            } else if( jparam.getValueClassName().equals( "java.lang.BigDecimal" )){
                Object param = parameters.get( jparam.getName());
                System.out.println( "1" + jparam.getValueClassName() ) ;
                parameters.put( jparam.getName(), new BigDecimal( (Double) parameters.get(jparam.getName() ) ) );
                System.out.println( "2" + jparam.getValueClassName() ) ;
            }
            //}catch(Exception e){
                        //e.printStackTrace();
            //}
        }

        System.out.println( "Before Filling report " );

        // Fill in report
        String language;
        if ( report.getQuery() == null )
            language = "";
        else
            language = report.getQuery().getLanguage();

        if( language.equalsIgnoreCase( "XPATH")  ){
            try {
                // If available, use a CSV file because it's faster to process.
                // Otherwise we'll use an XML file.
                if ( connectionParameters.containsKey("csv") ) {
                    JRCsvDataSource dataSource = new JRCsvDataSource( new File( (String)connectionParameters.get("csv") ), "utf-8" );
                    dataSource.setUseFirstRowAsHeader( true );
                    dataSource.setDateFormat( new SimpleDateFormat( "yyyy-MM-dd mm:hh:ss" ) );
                    jasperPrint = JasperFillManager.fillReport( report, parameters, dataSource );
                } else {
                    JRXmlDataSource dataSource = new JRXmlDataSource( (String)connectionParameters.get("xml"), "/data/record" );
                    dataSource.setDatePattern( "yyyy-MM-dd mm:hh:ss" );
                    dataSource.setNumberPattern( "#######0.##" );
                    dataSource.setLocale( Locale.ENGLISH );
                    jasperPrint = JasperFillManager.fillReport( report, parameters, dataSource );
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
            } catch (Exception e){
                e.printStackTrace();
            }
        } else if( language.equalsIgnoreCase( "SQL")  ) {
            try {
                Connection connection = getConnection( connectionParameters );
                jasperPrint = JasperFillManager.fillReport( report, parameters, connection );
            } catch( Exception e ){
                e.printStackTrace();
            }
        } else {
            JREmptyDataSource dataSource = new JREmptyDataSource();
            jasperPrint = JasperFillManager.fillReport( report, parameters, dataSource );
        }

        // Create output file
        File outputFile = new File( outputPath );
        JRAbstractExporter exporter;

        String output;
        if ( connectionParameters.containsKey( "output" ) )
            output = (String)connectionParameters.get("output");
        else
            output = "pdf";

        System.out.println( "EXPORTING..." );
        if ( output.equalsIgnoreCase( "html" ) ) {
            exporter = new JRHtmlExporter();
            exporter.setParameter(JRHtmlExporterParameter.IS_USING_IMAGES_TO_ALIGN, Boolean.FALSE);
            exporter.setParameter(JRHtmlExporterParameter.HTML_HEADER, "");
            exporter.setParameter(JRHtmlExporterParameter.BETWEEN_PAGES_HTML, "");
            exporter.setParameter(JRHtmlExporterParameter.IS_REMOVE_EMPTY_SPACE_BETWEEN_ROWS, Boolean.TRUE);
            exporter.setParameter(JRHtmlExporterParameter.HTML_FOOTER, "");
        } else if ( output.equalsIgnoreCase( "csv" ) ) {
            exporter = new JRCsvExporter();
        } else if ( output.equalsIgnoreCase( "xls" ) ) {
            exporter = new JRXlsExporter();
        } else if ( output.equalsIgnoreCase( "rtf" ) ) {
            exporter = new JRRtfExporter();
        } else if ( output.equalsIgnoreCase( "odt" ) ) {
            exporter = new JROdtExporter();
        } else if ( output.equalsIgnoreCase( "ods" ) ) {
            exporter = new JROdsExporter();
        } else if ( output.equalsIgnoreCase( "text" ) ) {
            exporter = new JRTextExporter();
        } else {
            exporter = new JRPdfExporter();
        }
        exporter.setParameter(JRExporterParameter.JASPER_PRINT, jasperPrint);
        exporter.setParameter(JRExporterParameter.OUTPUT_FILE, outputFile);
        exporter.exportReport();
        System.out.println( "EXPORTED." );
        return true; 
    }

    public static Connection getConnection( Hashtable datasource ) throws java.lang.ClassNotFoundException, java.sql.SQLException { 
        Connection connection; 
        Class.forName("org.postgresql.Driver"); 
        connection = DriverManager.getConnection( (String)datasource.get("dsn"), (String)datasource.get("user"), 
        (String)datasource.get("password") ); 
        connection.setAutoCommit(true); 
        return connection; 
    }


    public static void main (String [] args) {
        try {
            System.out.println("Attempting to start XML-RPC Server...");
            WebServer server = new WebServer(8090);
            XmlRpcServer xmlRpcServer = server.getXmlRpcServer();

            PropertyHandlerMapping phm = new PropertyHandlerMapping();
            phm.addHandler("Report", JasperServer.class);
            xmlRpcServer.setHandlerMapping(phm);

            server.start();
            System.out.println("Started successfully.");
            System.out.println("Accepting requests. (Halt program to stop.)");
        } catch (Exception exception) {
            System.err.println("Jasper Server: " + exception);
        }
    }
}
