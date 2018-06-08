/*
 * Copyright Public Record Office Victoria 2015
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 1.0 February 2015
 */
package fileharvestgui;

/**
 * This class represents a fatal error that occurred when constructing a VEO.
 * A fatal error is one that means it is not possible to continue to process any
 * VEOs.
 */
public class AppFatal extends AppError {

    /**
     * Construct a new VEOFatal with appropriate error message
     *
     * @param s the error message to return
     */
    public AppFatal(String s) {
        super(s);
    }

    /**
     * Construct a new VEOFatal from a class constructor giving error number.
     * This constructor should be used when it is desired to test that this
     * error is handled correctly.
     *
     * @param errno unique error number
     * @param s error message
     */
    public AppFatal(int errno, String s) {
        super(s);
        this.errno = errno;
    }

    /**
     * Construct a new VEOFatal from a class constructor giving error number
     *
     * @param classname name of class generating error
     * @param errno unique error number
     * @param s error message
     */
    public AppFatal(String classname, int errno, String s) {
        super(classname, errno, s);
    }

    /**
     * Construct a new VEOFatal from a particular method giving error number
     *
     * @param classname name of class generating error
     * @param method name of method in which error occurred
     * @param errno unique error number
     * @param s error message
     */
    public AppFatal(String classname, String method, int errno, String s) {
        super(classname, method, errno, s);
    }
}
