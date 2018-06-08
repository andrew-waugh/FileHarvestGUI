/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fileharvestgui;

import VEOCreate.CreateVEO;
import VEOCreate.Fragment;
import VERSCommon.PFXUser;
import VERSCommon.VEOError;
import VERSCommon.VEOFatal;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 *
 * @author Andrew
 */
public class FileHarvest {

    private static String classname = "FileHarvest"; // for reporting
    private static ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private Runtime r;
    private long freemem;

    // global variables storing information about this export (as a whole)
    private Path outputDirectory;   // directory in which VEOS are to be generated
    private ArrayList<String> bases;// list of base directories to harvest
    private Path baseDirectory;     // directory to harvest
    private Path ignoreFile;        // file of things to ignore when harvesting
    private Path descFile;          // text file containing an archival description of the harvest
    private int exportCount;        // number of exports processed
    private Path templateDirectory; // directory that contains all the files needed to build the directory
    private boolean ignoreFileWithNoExtension; // if true, don't harvest any files with no file extension
    private TreeMap<String, String> extensionsIgnored; // list of extensions to ignore
    private TreeMap<String, String> directoriesIgnored; // list of directories to ignore
    private ArrayList<Pattern> ignorePatterns; // list of file name patterns to ignore
    private ArrayList<String> validLTPF; // list of valid long term preservation formats
    private String hashAlg;         // hash algorithm to use (default SHA-512)
    private Path pfxFile;           // the pfx file containing the private key to sign the VEO
    private String pfxFilePassword; // the password for the pfx file
    private boolean debug;          // true if in debug mode
    private boolean verbose;        // true if in verbose output mode
    private String userId;          // user performing the converstion
    private PFXUser user;           // User that will sign the VEOs
    private String archivalDesc;    // precanned description of this harvesting
    private Fragment recordAGLS;    // template for the AGLS objMetadata describing the record as a whole
    private String[] recMetadata;   // collection of metadata describing this record
    private Fragment directoryAGLS; // template for the AGLS objMetadata describing a directory
    private Fragment fileAGLS; // template for the AGLS objMetadata describing a file
    private boolean addedDummyLTPF;  // true if dummy LTPF has been added to VEO
    private int iocnt;              // count of the information objects added (used to make a unique id)

    // variables for this capture
    private Path veoDirectory;      // directory in which to create the VEO content for this file

    // private final static Logger rootLog = Logger.getLogger("FileHarvest");
    private final static Logger LOG = Logger.getLogger("FileHarvest.FileHarvestAnalysis");

    /**
     * Default constructor
     *
     * @param args arguments passed to program
     * @throws AppFatal if a fatal error occurred
     */
    public FileHarvest(String args[]) throws AppFatal {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
        setup();

        // process command line arguments
        configure(args);

        // read ignore file
        if (ignoreFile != null) {
            readIgnoreFile();
        }

        if (descFile != null) {
            archivalDesc = readDescFile();
        } else {
            archivalDesc = " ";
        }

        // read valid long term preservation formats
        getValidLTPF(Paths.get(templateDirectory.toString(), "VERSltpf.txt"));
    }

    /**
     * Constructor called when configuring from the GUI using a Job
     * specification
     *
     * @param j the Job specification
     * @param hndlr a Log handler
     * @throws AppFatal something went wrong
     */
    public FileHarvest(Job j, Handler hndlr) throws AppFatal {
        Handler h[];
        int i;

        // sanity checks
        if (j.pfxFile == null) {
            throw new AppFatal("A PFX file must be specified");
        }

        // remove any handlers associated with the LOG & log messages aren't to
        // go to the parent
        h = LOG.getHandlers();
        for (i = 0; i < h.length; i++) {
            LOG.removeHandler(h[i]);
        }
        LOG.setUseParentHandlers(false);

        // add log handler from calling program
        LOG.addHandler(hndlr);
        System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s");

        // set up and configure
        setup();
        verbose = false;
        debug = false;
        hashAlg = j.hashAlg;
        templateDirectory = checkFile("veo template directory", j.templateDir, true);
        pfxFile = checkFile("PFX file", j.pfxFile, false);
        pfxFilePassword = j.pfxFilePassword;
        try {
            user = new PFXUser(pfxFile.toString(), pfxFilePassword);
        } catch (VEOError ve) {
            throw new AppFatal(ve.toString());
        }
        outputDirectory = checkFile("output directory", j.outputDir, true);

        //process the excluded files
        for (i = 0; i < j.filesToExclude.size(); i++) {
            try {
                ignorePatterns.add(Pattern.compile(j.filesToExclude.get(i)));
            } catch (PatternSyntaxException pse) {
                LOG.log(Level.WARNING, "Invalid pattern: ''{0}'' when ignoring files: {1}", new Object[]{j.filesToExclude.get(i), pse.getMessage()});
            }
        }

        //process the excluded folders
        for (i = 0; i < j.foldersToExclude.size(); i++) {
            String s = j.foldersToExclude.get(i);
            if (!directoriesIgnored.containsKey(s)) {
                directoriesIgnored.put(s, s);
            }
        }

        // read valid long term preservation formats
        getValidLTPF(templateDirectory.resolve("VERSltpf.txt"));

        // descFile = checkFile("archival description file", j.archiveDescFile, false);
        // ignoreFile = checkFile("Ignore File", j.ignoreFile, false);
        // read the objMetadata fragments
        try {
            recordAGLS = Fragment.parseTemplate(templateDirectory.resolve("recordAGLS.txt").toFile());
            directoryAGLS = Fragment.parseTemplate(templateDirectory.resolve("directoryAGLS.txt").toFile());
            fileAGLS = Fragment.parseTemplate(templateDirectory.resolve("fileAGLS.txt").toFile());
        } catch (VEOFatal vf) {
            throw new AppFatal("Template directory did not contain the required template: " + vf.getMessage());
        }
        archivalDesc = " ";
    }

    /**
     * Initialise all the global variables
     */
    final void setup() {

        // set up default global variables
        LOG.setLevel(Level.WARNING);
        baseDirectory = null;
        bases = new ArrayList<>();
        outputDirectory = Paths.get(".");
        descFile = null;
        templateDirectory = Paths.get(".");
        exportCount = 0;
        debug = false;
        verbose = false;
        ignoreFile = null;
        ignoreFileWithNoExtension = false;
        extensionsIgnored = new TreeMap<>();
        directoriesIgnored = new TreeMap<>();
        ignorePatterns = new ArrayList<>();
        validLTPF = new ArrayList<>();
        hashAlg = "SHA-512";
        pfxFile = null;
        pfxFilePassword = null;
        userId = System.getProperty("user.name");
        if (userId == null) {
            userId = "Unknown user";
        }
        user = null;
        recordAGLS = null;
        directoryAGLS = null;
        recMetadata = new String[6];
        fileAGLS = null;
        addedDummyLTPF = false;
        iocnt = 1;

        // variables for the whole processing
        r = Runtime.getRuntime();
    }

    /**
     * Configure
     *
     * This method gets the options for this run of the file harvester from the
     * command line. See the comment at the start of this file for the command
     * line arguments.
     *
     * @param args[] the command line arguments
     * @param Exception if a fatal error occurred
     */
    private void configure(String args[]) throws AppFatal {
        int i;
        String usage = "fileHarvest [-v] [-d] [-a <descFile>] [-o <directory>] [-t templateDirectory] [-h hashAlg] [-s pfxFile password] [-i ignoreFile] directory";

        // process command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i]) {

                    // verbose?
                    case "-v":
                        verbose = true;
                        LOG.setLevel(Level.INFO);
                        i++;
                        break;

                    // debug?
                    case "-d":
                        debug = true;
                        LOG.setLevel(Level.FINE);
                        i++;
                        break;

                    // get hash algorithm
                    case "-ha":
                        i++;
                        hashAlg = args[i];
                        i++;
                        break;

                    // '-t' specifies the directory containing the files necessary to build the veo
                    case "-t":
                        i++;
                        templateDirectory = checkFile("veo construction files", args[i], true);
                        i++;
                        break;

                    // get pfx file
                    case "-s":
                        i++;
                        pfxFile = checkFile("PFX file", args[i], false);
                        i++;
                        pfxFilePassword = args[i];
                        i++;
                        try {
                            user = new PFXUser(pfxFile.toString(), pfxFilePassword);
                        } catch (VEOError ve) {
                            throw new AppFatal(ve.toString());
                        }
                        break;

                    // '-o' specifies output directory
                    case "-o":
                        i++;
                        outputDirectory = checkFile("output directory", args[i], true);
                        i++;
                        break;

                    // '-a' specifies a text file containing an archival description of the harvest
                    case "-a":
                        i++;
                        descFile = checkFile("archival description file", args[i], false);
                        i++;
                        break;

                    // '-i' specifies a file containing things to ignore when harvesting
                    case "-i":
                        i++;
                        ignoreFile = checkFile("Ignore File", args[i], false);
                        i++;
                        break;

                    default:
                        // if unrecognised arguement, print help string and exit
                        if (args[i].charAt(0) == '-') {
                            throw new AppFatal("Unrecognised argument '" + args[i] + "' Usage: " + usage);
                        }

                        // if doesn't start with '-' assume a file or directory name
                        bases.add(args[i]);
                        i++;
                        break;
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new AppFatal("Missing argument. Usage: " + usage);
        }

        // check to see if at least one file or directory is specified
        if (pfxFile == null) {
            throw new AppFatal("You must specify a PFX file using the -s command line argument");
        }

        // read the objMetadata fragments
        try {
            recordAGLS = Fragment.parseTemplate(Paths.get(templateDirectory.toString(), "recordAGLS.txt").toFile());
            directoryAGLS = Fragment.parseTemplate(Paths.get(templateDirectory.toString(), "directoryAGLS.txt").toFile());
            fileAGLS = Fragment.parseTemplate(Paths.get(templateDirectory.toString(), "fileAGLS.txt").toFile());
        } catch (VEOFatal vf) {
            throw new AppFatal("Template directory did not contain the required template: " + vf.getMessage());
        }
        // log generic things
        if (debug) {
            LOG.log(Level.INFO, "Debug mode is selected");
        }
        if (verbose) {
            LOG.log(Level.INFO, "Verbose output is selected");
        }
        LOG.log(Level.INFO, "VEO template directory is ''{0}''", new Object[]{templateDirectory.toString()});
        if (descFile != null) {
            LOG.log(Level.INFO, "Archival Description File is ''{0}''", new Object[]{descFile.toString()});
        } else {
            LOG.log(Level.INFO, "No Archival Description File is specified");
        }
        if (ignoreFile != null) {
            LOG.log(Level.INFO, "Ignore File is ''{0}''", new Object[]{ignoreFile.toString()});
        } else {
            LOG.log(Level.INFO, "No Ignore File is specified");
        }
        LOG.log(Level.INFO, "Output directory is ''{0}''", new Object[]{outputDirectory.toString()});
        LOG.log(Level.INFO, "User running harvest: ''{0}''", new Object[]{userId});
        LOG.log(Level.INFO, "PFX user is ''{0}''", new Object[]{user.getUserId()});
    }

    /**
     * Check a file to see that it exists and is of the correct type (regular
     * file or directory). The program terminates if an error is encountered.
     *
     * @param type a String describing the file to be opened
     * @param name the file name to be opened
     * @param isDirectory true if the file is supposed to be a directory
     * @throws VEOFatal if the file does not exist, or is of the correct type
     * @return the File opened
     */
    private Path checkFile(String type, String name, boolean isDirectory) throws AppFatal {
        return checkFile(type, Paths.get(name), isDirectory);
    }

    private Path checkFile(String type, Path p, boolean isDirectory) throws AppFatal {
        if (p == null) {
            throw new AppFatal(classname, 6, type + " '" + p.toString() + "' is null");
        }
        if (!Files.exists(p)) {
            throw new AppFatal(classname, 6, type + " '" + p.toString() + "' does not exist");
        }
        if (isDirectory && !Files.isDirectory(p)) {
            throw new AppFatal(classname, 7, type + " '" + p.toString() + "' is a file not a directory");
        }
        if (!isDirectory && Files.isDirectory(p)) {
            throw new AppFatal(classname, 8, type + " '" + p.toString() + "' is a directory not a file");
        }
        return p;
    }

    /**
     * Read the archival description file. This method reads a text file that
     * contains an archival description of the harvest.
     */
    private String readDescFile() {
        FileReader fr = null;
        BufferedReader br = null;
        String line;
        StringBuilder sb;

        sb = new StringBuilder();
        try {
            fr = new FileReader(descFile.toString());
            br = new BufferedReader(fr);
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (FileNotFoundException fnfe) {
            LOG.log(Level.WARNING, "Ignore File ''{0}'' does not exist", new Object[]{ignoreFile.toString()});
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Error when reading Ignore File ''{0}'': ''{1}''", new Object[]{ignoreFile.toString(), ioe.getMessage()});
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                /* ignore */
            }
            try {
                if (fr != null) {
                    fr.close();
                }
            } catch (IOException e) {
                /* ignore */
            }
        }
        return sb.toString();
    }

    /**
     * This method reads the Ignore File (conventionally "IgnoreFile.txt"). This
     * file contains a list of patterns, file extensions and directories to
     * ignore in the harvest
     *
     * Extensions to be ignored are specified by lines of the following format
     * "ignoreExtension" [/t&lt;extension&gt; | "noExtension"]+ Extensions are
     * specified as strings that are matched ignoring case (e.g. "DOC", "doc",
     * and "Doc" are all equivalent). The leading '.' may be present, but is
     * ignored. (e.g. ".doc" and "doc" are equivalent). The special string
     * "noExtension" specifies that any file without an extension is to be
     * ignored. ignoreExtension lines can contain multiple extensions, and
     * multiple ignoreExtension lines can be specified. Extensions can appear
     * multiple times.
     *
     * Directories to be ignored are specified by lines of the following format
     * "ignoreDirectory" [/t&lt;directory&gt;] If a directory is ignored, it and
     * all its contents will not be harvested. Directories are specified
     * relative to the root of the harvest. Multiple directories can be
     * specified in one ignore directory line, and multiple ignore directory
     * lines can appear in the file. Directories can be specified multiple
     * times.
     */
    private void readIgnoreFile() {
        FileReader fr = null;
        BufferedReader br = null;
        String line;
        String[] tokens;
        int i;

        try {
            fr = new FileReader(ignoreFile.toString());
            br = new BufferedReader(fr);
            while ((line = br.readLine()) != null) {
                // System.out.println(line);
                tokens = line.split("\t");
                switch (tokens[0].toLowerCase()) {
                    case "ignoreextension":
                        if (tokens.length < 2) {
                            LOG.log(Level.WARNING, "Ignore File ''{0}'' line ''{1}'' does not have at least two tokens", new Object[]{ignoreFile.toString(), line});
                            continue;
                        }
                        for (i = 1; i < tokens.length; i++) {
                            tokens[i] = tokens[i].toLowerCase().trim();

                            // ignore tokens that are empty or are just a dot
                            if (tokens[i].equals("") || tokens[i].equals(" ") || tokens[i].equals(".")) {
                                continue;
                            }

                            // get rid of a leading '.' if present
                            if (tokens[i].startsWith(".")) {
                                tokens[i] = tokens[i].substring(1);
                            }

                            // special handling for "noextension" keyword
                            if (tokens[i].equals("noextension")) {
                                ignoreFileWithNoExtension = true;
                            } else if (!extensionsIgnored.containsKey(tokens[i])) {
                                extensionsIgnored.put(tokens[i], tokens[i]);
                            }
                        }
                        break;
                    case "ignorepattern":
                        if (tokens.length < 2) {
                            LOG.log(Level.WARNING, "Ignore File ''{0}'' line ''{1}'' does not have at least two tokens", new Object[]{ignoreFile.toString(), line});
                            continue;
                        }
                        for (i = 1; i < tokens.length; i++) {
                            tokens[i] = tokens[i].trim();
                            try {
                                ignorePatterns.add(Pattern.compile(tokens[i]));
                            } catch (PatternSyntaxException pse) {
                                LOG.log(Level.WARNING, "Ignore File ''{0}'' line ''{1}'' invalid pattern: {2}", new Object[]{ignoreFile.toString(), line, pse.getMessage()});
                            }
                        }
                        break;
                    case "ignoredirectory":
                        if (tokens.length < 2) {
                            LOG.log(Level.WARNING, "Ignore File ''{0}'' line ''{1}'' does not have at least two tokens", new Object[]{ignoreFile.toString(), line});
                            continue;
                        }
                        for (i = 1; i < tokens.length; i++) {
                            tokens[i] = tokens[i].trim();
                            if (tokens[i].equals("") || tokens[i].equals(" ")) {
                                continue;
                            }
                            if (!directoriesIgnored.containsKey(tokens[i])) {
                                directoriesIgnored.put(Paths.get(".", tokens[i]).toString(), tokens[i]);
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (FileNotFoundException fnfe) {
            LOG.log(Level.WARNING, "Ignore File ''{0}'' does not exist", new Object[]{ignoreFile.toString()});
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Error when reading Ignore File ''{0}'': ''{1}''", new Object[]{ignoreFile.toString(), ioe.getMessage()});
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                /* ignore */
            }
            try {
                if (fr != null) {
                    fr.close();
                }
            } catch (IOException e) {
                /* ignore */
            }
        }
    }

    /**
     * Describe ignore file. This method turns the ignore file into text
     */
    private String describeIgnoreFile() {
        StringBuilder sb;
        Iterator<String> it;
        Iterator<Pattern> itp;
        String s;

        sb = new StringBuilder();

        it = directoriesIgnored.keySet().iterator();
        if (!it.hasNext()) {
            xmlEncode(sb, "All subdirectories in '" + baseDirectory.toString() + "' have been harvested\n");
            xmlEncode(sb, "When harvesting '" + baseDirectory.toString() + "' the following subdirectories were specified to be ignored:\n");
            while (it.hasNext()) {
                s = directoriesIgnored.get(it.next());
                xmlEncode(sb, "\t'" + s + "'\n");
            }
        }

        it = extensionsIgnored.keySet().iterator();
        if (!it.hasNext()) {
            xmlEncode(sb, "No file types in '" + baseDirectory.toString() + "' have been ignored due to a specified extension\n");
        } else {
            xmlEncode(sb, "When harvesting '" + baseDirectory.toString() + "' the following file types were specified to be ignored:");
            while (it.hasNext()) {
                s = extensionsIgnored.get(it.next());
                xmlEncode(sb, "." + s + " ");
            }
            xmlEncode(sb, "\n");
        }
        if (ignoreFileWithNoExtension) {
            xmlEncode(sb, "\nWhen harvesting '" + baseDirectory.toString() + "', without a file extension were specified to be ignored.\n");
        }

        itp = ignorePatterns.iterator();
        if (!itp.hasNext()) {
            xmlEncode(sb, "No file types in '" + baseDirectory.toString() + "' have been ignored due to a specified pattern\n");
        } else {
            xmlEncode(sb, "When harvesting '" + baseDirectory.toString() + "' the file names matching the following patterns were specified to be ignored:");
            while (itp.hasNext()) {
                s = itp.next().pattern();
                xmlEncode(sb, s + " ");
            }
            xmlEncode(sb, "\n");
        }
        return sb.toString();
    }

    /**
     * getValidLTPF
     *
     * This reads a set of file extensions from the file 'ltpf.txt' in the
     * template directory. A file extension is the characters after the '.' in a
     * file name (e.g. 'doc', or 'pdf')
     */
    private void getValidLTPF(Path labels) {
        FileReader fr = null;
        BufferedReader br = null;
        String line;

        try {
            fr = new FileReader(labels.toString());
            br = new BufferedReader(fr);
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                validLTPF.add(line);
            }
        } catch (FileNotFoundException fnfe) {
            LOG.log(Level.WARNING, "Valid LTPF file ''{0}'' does not exist", new Object[]{labels.toString()});
        } catch (IOException ioe) {
            LOG.log(Level.WARNING, "Error when reading Valid LTPF file ''{0}'': ''{1}''", new Object[]{labels.toString(), ioe.getMessage()});
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                /* ignore */
            }
            try {
                if (fr != null) {
                    fr.close();
                }
            } catch (IOException e) {
                /* ignore */
            }
        }
    }

    /**
     * Test to see if file is a LTPF The file extension is extracted from the
     * filename and looked up in the array of valid LTPFs
     */
    private boolean isLTPF(String filename) {
        String filetype;
        int i;

        if ((i = filename.lastIndexOf('.')) == -1) {
            return false;
        }
        filetype = filename.substring(i).toLowerCase();
        return validLTPF.contains(filetype);
    }

    /**
     * XML encode string
     *
     * Make sure any XML special characters in a string are encoded
     */
    private void xmlEncode(StringBuilder out, String in) {
        int i;
        char c;

        if (in == null) {
            return;
        }
        for (i = 0; i < in.length(); i++) {
            c = in.charAt(i);
            switch (c) {
                case '&':
                    if (!in.regionMatches(true, i, "&amp;", 0, 5)
                            && !in.regionMatches(true, i, "&lt;", 0, 4)
                            && !in.regionMatches(true, i, "&gt;", 0, 4)
                            && !in.regionMatches(true, i, "&quot;", 0, 6)
                            && !in.regionMatches(true, i, "&apos;", 0, 6)) {
                        out.append("&amp;");
                    }
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&apos;");
                    break;
                default:
                    out.append(c);
                    break;
            }
        }
    }

    /**
     * Process the base directory
     */
    static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Process the a single directory
     *
     * @param directory the directory to process
     * @return true if the creation of a VEO succeeded
     */
    public boolean process(Path directory) {
        try {
            createVEO(directory);
        } catch (VEOError ve) {
            LOG.log(Level.WARNING, "VEO ''{0}.veo.zip'' incomplete because:\n{1}", new Object[]{directory.toString(), ve.getMessage()});
            return false;
        }
        return true;
    }

    /**
     * Create VEO
     *
     * This method creates a new VEO
     *
     * @param baseDirectory	the file to parse
     * @throws VEOError if an error occurred that prevented the processing of
     * this XML file
     */
    private void createVEO(Path baseDirectory) throws VEOError {
        long l;
        CreateVEO cv;
        Path p;
        String recordName;      // name of this record element (from the file, without the final '.xml')
        String description[] = {"Created with FileHarvest"};
        String errors[] = {""};
        StringBuffer res;

        // check parameters
        if (baseDirectory == null) {
            throw new VEOFatal("Passed null base directory to be processed");
        }

        // reset, free memory, and print status
        baos.reset();
        r.gc();
        l = r.freeMemory();
        // LOG.log(Level.WARNING, "{0} Processing: ''{1}''", new Object[]{versDateTime(false, 0), baseDirectory.toString()});
        freemem = l;

        // get the record name from the name of the base directory
        recordName = "FSC-" + baseDirectory.getFileName().toString() + "-" + versDateTime(true, System.currentTimeMillis());

        // create a record directory in the output directory
        p = Paths.get(outputDirectory.toString(), recordName + ".veo");
        if (!deleteDirectory(p)) {
            throw new VEOError("Arrgh: directory '" + p.toString() + "' already exists & couldn't be deleted");
        }
        try {
            veoDirectory = Files.createDirectory(p);
        } catch (IOException ioe) {
            throw new VEOError("Arrgh: could not create record directory '" + p.toString() + "': " + ioe.toString());
        }

        // capture metadata about this record
        recMetadata[0] = "http://www.prov.vic.gov.au/records/" + recordName;
        try {
            recMetadata[1] = baseDirectory.toRealPath().toString();
        } catch (IOException ioe) {
            throw new VEOError("Failed to get real path of base directory: " + ioe.toString());
        }
        recMetadata[2] = recordName;
        recMetadata[3] = userId;
        recMetadata[4] = versDateTime(true, System.currentTimeMillis());
        recMetadata[5] = archivalDesc;

        // we haven't added the dummy LTPF file yet
        addedDummyLTPF = false;

        // create VEO...
        cv = new CreateVEO(outputDirectory, recordName, hashAlg, verbose);
        try {
            cv.addVEOReadme(templateDirectory);
            cv.addEvent(versDateTime(false, System.currentTimeMillis()), "Converted to VEO", userId, description, errors);
            cv.addContent(CreateVEO.AddMode.HARD_LINK, baseDirectory);
            res = null;
            try {
                processFile(cv, recordName, baseDirectory, baseDirectory, 1);
            } catch (VEOError ve) {
                LOG.log(Level.WARNING, "VEO ''{0}.veo.zip'' incomplete because:\n{1}", new Object[]{recordName, ve.getMessage()});
            }
            if (res != null) {
                if (res.charAt(res.length() - 1) == '\n') {
                    res.setCharAt(res.length() - 1, ' ');
                }
                LOG.log(Level.WARNING, "VEO ''{0}.veo.zip'' incomplete because:\n{1}", new Object[]{recordName, res.toString()});
            }
            cv.finishFiles();
            cv.sign(user, hashAlg);
            cv.finalise(true);
        } catch (VEOError ve) {
            cv.abandon(true);
            throw ve;
        } finally {
            System.gc();
        }

        // count the number of exports successfully processed
        exportCount++;
    }

    /**
     * Recursively delete a directory
     */
    private boolean deleteDirectory(Path directory) {
        DirectoryStream<Path> ds;
        boolean failed;

        failed = false;
        try {
            if (!Files.exists(directory)) {
                return true;
            }
            ds = Files.newDirectoryStream(directory);
            for (Path p : ds) {
                if (!Files.isDirectory(p)) {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        failed = true;
                    }
                } else {
                    failed |= !deleteDirectory(p);
                }
            }
            ds.close();
            if (!failed) {
                Files.delete(directory);
            }
        } catch (IOException e) {
            failed = true;
        }
        return !failed;
    }

    /**
     * versDateTime
     *
     * Returns a date and time in the standard VERS format (see PROS 99/007
     * (Version 2), Specification 2, p146
     *
     * @param fssafe true if we want a date/time that is file system safe
     * @param ms	milliseconds since the epoch (if zero, return current
     * date/time)
     */
    private String versDateTime(boolean fssafe, long ms) {
        Date d;
        SimpleDateFormat sdf;
        TimeZone tz;
        String s;

        tz = TimeZone.getDefault();
        if (fssafe) {
            sdf = new SimpleDateFormat("yyyy-MM-dd'T'HHmmssZ");
        } else {
            sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        }
        sdf.setTimeZone(tz);
        if (ms == 0) {
            d = new Date();
        } else {
            d = new Date(ms);
        }
        s = sdf.format(d);
        if (!fssafe) {
            s = s.substring(0, 22) + ":" + s.substring(22, 24);
        }
        return s;
    }

    private String versTime(boolean fssafe, long ms) {
        Date d;
        SimpleDateFormat sdf;
        TimeZone tz;
        String s;

        tz = TimeZone.getDefault();
        if (fssafe) {
            sdf = new SimpleDateFormat("HHmmss");
        } else {
            sdf = new SimpleDateFormat("HH:mm:ss");
        }
        sdf.setTimeZone(tz);
        if (ms == 0) {
            d = new Date();
        } else {
            d = new Date(ms);
        }
        s = sdf.format(d);
        if (!fssafe) {
            s = s.substring(0, 22) + ":" + s.substring(22, 24);
        }
        return s;
    }

    /**
     * Process the specified directory
     */
    private void processFile(CreateVEO cv, String recordName, Path file, Path baseDirectory, int depth) throws VEOError {
        DirectoryStream<Path> ds;
        int i;
        String filename, ext;
        BasicFileAttributes bfa;
        String[] objMetadata = new String[7];
        Path reportedFile;

        // sanity check
        if (!Files.exists(file)) {
            throw new VEOFatal("VEO incomplete because file/directory '" + file.toString() + "' was supposed to exist, but does not");
        }

        // if this is the baseDirectory, report this, otherwise report relative to the baseDirectory
        if (file == baseDirectory) {
            reportedFile = baseDirectory;
        } else {
            reportedFile = baseDirectory.relativize(file);
        }

        // should this file/directory be ignored?
        filename = file.getFileName().toString();
        Iterator<Pattern> itp;
        Pattern p1;
        itp = ignorePatterns.iterator();
        while (itp.hasNext()) {
            p1 = itp.next();
            if (p1.matcher(filename).matches()) {
                LOG.log(Level.WARNING, "File or directory ''{0}'' (and its contents) were not included as the file name matched pattern {1}", new Object[]{reportedFile.toString(), p1.pattern()});
                return;
            }
        }

        if (Files.isDirectory(file)) {
            if (directoriesIgnored.containsKey(file.toString())) {
                LOG.log(Level.WARNING, "Directory ''{0}'' (and its contents) were not included due to capture configuration", new Object[]{reportedFile.toString()});
                return;
            }
        } else {
            i = filename.lastIndexOf(".");
            if (i == -1 && ignoreFileWithNoExtension) {
                LOG.log(Level.WARNING, "File ''{0}'' was not included as files with no file extensions were ignored", new Object[]{reportedFile.toString()});
                return;
            }
            ext = filename.substring(i + 1);
            if (extensionsIgnored.containsKey(ext)) {
                LOG.log(Level.WARNING, "File ''{0}'' was not included as files with no file extension ''{1}'' were ignored", new Object[]{reportedFile.toString(), ext});
                return;
            }
        }

        // print information about this object
        try {
            cv.addInformationObject(file.toRealPath().toString(), depth);
        } catch (IOException ioe) {
            throw new VEOFatal("Failed to process directory: " + ioe.getMessage());
        }

        // if at the root, add metadata about this record as a whole
        if (depth == 1) {
            cv.addMetadataPackage(recordAGLS, recMetadata);
            addHarvestDescription(cv, baseDirectory);
            objMetadata[0] = "http://www.prov.vic.gov.au/records/" + recordName;
        } else {
            objMetadata[0] = "http://www.prov.vic.gov.au/records/" + recordName + "/" + iocnt;
            iocnt++;
        }

        // get descriptive information about this file or directory        
        objMetadata[1] = baseDirectory.getParent().relativize(file).toString();
        try {
            objMetadata[2] = Files.getOwner(file).getName();
            bfa = Files.readAttributes(file, BasicFileAttributes.class);
        } catch (IOException ioe) {
            throw new VEOFatal("Error when writing control file. Failed to get file attributes for file '" + file.toAbsolutePath() + toString() + ": " + ioe.getMessage());
        }
        objMetadata[3] = bfa.creationTime().toString();
        objMetadata[4] = bfa.lastModifiedTime().toString();
        objMetadata[5] = bfa.lastAccessTime().toString();
        objMetadata[6] = Long.toString(bfa.size());

        if (Files.isDirectory(file)) {
            cv.addMetadataPackage(directoryAGLS, objMetadata);
        } else {
            cv.addMetadataPackage(fileAGLS, objMetadata);
        }

        // if a directory, process the children
        if (Files.isDirectory(file)) {
            try {
                LOG.log(Level.FINE, "Directory ''{0}'' was added", new Object[]{file.toString()});
                ds = Files.newDirectoryStream(file);
                for (Path p : ds) {
                    processFile(cv, recordName, p, baseDirectory, depth + 1);
                }
                ds.close();
            } catch (IOException e) {
                throw new VEOFatal("Failed to process directory '" + file.toAbsolutePath() + toString() + "': " + e.getMessage());
            }
        } else {
            cv.addInformationPiece("file");
            cv.addContentFile(baseDirectory.getParent().relativize(file).toString());
            if (!isLTPF(file.getFileName().toString())) {
                addDummyLTPF(cv, reportedFile);
            }
            LOG.log(Level.FINE, "File ''{0}'' was added", new Object[]{file.toString()});
        }
    }

    /**
     * Add a dummy long term preservation file
     */
    private void addDummyLTPF(CreateVEO cv, Path file) {
        Path p;

        // add the dummy LTPF to the VEO if we haven't already done so
        if (!addedDummyLTPF) {
            p = Paths.get(templateDirectory.toString(), "DummyContent");
            try {
                cv.addContent(CreateVEO.AddMode.HARD_LINK, p);
            } catch (VEOError ve) {
                LOG.log(Level.WARNING, "Cannot add dummy LTPF {0} because: {1}", new Object[]{p.toString(), ve.getMessage()});
                return;
            }
            addedDummyLTPF = true;
        }

        // add the content file to the current information piece
        try {
            cv.addContentFile("DummyContent/DummyLTPF.txt");
        } catch (VEOError ve) {
            LOG.log(Level.WARNING, "Cannot add ''DummyContent/DummyLTPF.txt'' because: {0}", new Object[]{ve.getMessage()});
        }
        LOG.log(Level.WARNING, "WARNING: {0} is not a valid long term preservation format", file.toString());
    }

    /**
     * Add a description of the harvest paramenters to the root information
     * object
     */
    private void addHarvestDescription(CreateVEO cv, Path baseDirectory) throws VEOError {
        StringBuilder sb;
        Iterator<String> it;
        Iterator<Pattern> itp;
        Pattern pat;
        String s;

        sb = new StringBuilder();

        sb.append("<rdf:RDF ");
        sb.append("xmlns:ex=\"http://www.agls.gov.au/agls/terms#\"\n");
        sb.append(">");
        sb.append("<rdf:Description rdf:about=\"http://www.example.org/124\">\n");
        sb.append(" <ex:baseDirectory >");
        xmlEncode(sb, baseDirectory.toString());
        sb.append("</ex:baseDirectory>\n");

        itp = ignorePatterns.iterator();
        if (!itp.hasNext()) {
            sb.append(" <ex:ignoredPatterns/>\n");
        } else {
            // sb.append(" <fsharvest:ignoredPatterns rdf:parseType=\"Literal\">\n");
            while (itp.hasNext()) {
                pat = itp.next();
                sb.append("  <ex:ignoredPattern>");
                xmlEncode(sb, pat.pattern());
                sb.append("</ex:ignoredPattern>\n");
            }
            // sb.append(" </fsharvest:ignoredPatterns>\n");
        }

        it = directoriesIgnored.keySet().iterator();
        if (!it.hasNext()) {
            sb.append(" <ex:ignoredDirectories/>\n");
        } else {
            // sb.append(" <fsharvest:ignoredDirectories rdf:parseType=\"Literal\">\n");
            while (it.hasNext()) {
                s = directoriesIgnored.get(it.next()).toString();
                sb.append("  <ex:ignoredDirectory>");
                xmlEncode(sb, s);
                sb.append("</ex:ignoredDirectory>\n");
            }
            // sb.append(" </fsharvest:ignoredDirectories>\n");
        }

        it = extensionsIgnored.keySet().iterator();
        if (!it.hasNext() && !ignoreFileWithNoExtension) {
            sb.append(" <ex:ignoredFileTypes/>\n");
        } else {
            // sb.append(" <fsharvest:ignoredFileTypes rdf:parseType=\"Literal\">\n");
            while (it.hasNext()) {
                s = extensionsIgnored.get(it.next());
                sb.append("  <ex:ignoredFileType>");
                xmlEncode(sb, "." + s);
                sb.append("</ex:ignoredFileType>\n");
            }
            if (ignoreFileWithNoExtension) {
                sb.append("  <ex:ignoredFileType>");
                xmlEncode(sb, "All files with no file extension");
                sb.append("</ex:ignoredFileType>\n");
            }
            // sb.append(" </fsharvest:ignoredFileTypes>\n");
        }
        sb.append("</rdf:Description>\n");
        sb.append("</rdf:RDF>\n");
        cv.addMetadataPackage("http://prov.vic.gov.au/vers/schema/FileHarvestDesc", "http://www.w3.org/1999/02/22-rdf-syntax-ns", sb);
    }

    /**
     * Report
     */
    private void report() {
        Iterator<String> it;
        String s;

        try {
            System.out.println("!\tFilesystem Harvest of '" + baseDirectory.toRealPath().toString() + "'");
        } catch (IOException ioe) {
            LOG.log(Level.SEVERE, "Failure when converting the base directory ''{0}'' to a real path: ''{1}''", new Object[]{baseDirectory, ioe.getMessage()});
        }
        System.out.println("!\tHarvested on ");
        System.out.println("!\tHarvested by " + userId);
        System.out.print("!\tThe following directories have not been harvested due to harvestor configuration:");
        it = directoriesIgnored.keySet().iterator();
        if (!it.hasNext()) {
            System.out.println(" None");
        } else {
            System.out.println("");
            while (it.hasNext()) {
                s = directoriesIgnored.get(it.next()).toString();
                System.out.println("!\t'" + s + "'");
            }
        }
        System.out.print("!\tThe following file types have not been harvested due to harvestor configuration:");
        it = extensionsIgnored.keySet().iterator();
        if (!it.hasNext()) {
            System.out.print(" None");
        } else {
            System.out.println("");
            while (it.hasNext()) {
                s = extensionsIgnored.get(it.next());
                System.out.println("!\t." + s);
            }
        }
        if (ignoreFileWithNoExtension) {
            System.out.println("!\tFiles without a file extension have not been harvested due to harvestor configuration:");
        }
    }

    /**
     * Main program
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        FileHarvest fh;

        try {
            fh = new FileHarvest(args);
            // fha.report();
            fh.process(Paths.get("."));
        } catch (Exception e) {
            System.out.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
