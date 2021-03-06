/*
 * Copyright (C) 2015-2016 Vinay Choudhary
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 */

package com.vinaychoudhary.common.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.os.Process;
import android.util.Log;

/**
 * The utility class that logs the messages based on a TAG. Only one object is created for same
 * tags. For logging to files, specify directory/filename or else keep it null. In case of null tag
 * as argument, {@value #DEFAULT_TAG} is used. <br>
 * <br>
 * If being used as library each library should use it as static library and not as shared library.
 *
 * @see For configuration : {@link LogConfig#setConfig(String, String, String, long)};
 * @author Vinay Choudhary
 */
@SuppressLint("DefaultLocale")
public class LogWrapper {

    public static final String DEFAULT_TAG = "DEFAULT-LOG-TAG";
    public static final String DEFAULT_FILE = "DEFAULT_LOGS.log";
    public static final String DEFAULT_DIR = "DEFAULT_LOG_DIR";

    /**
     * 3 MB is max size for a log file.
     */
    public static final long MAX_LOG_FILE_SIZE = 1024 * 1024 * 3;

    /**
     * 128 KB is minimum size
     */
    private static final long MIN_LOG_FILE_SIZE = 1024 * 128;

    /**
     * The Log levels. Based on Android Log Levels.
     */
    public enum LogLevel {
        VERBOSE("V"), DEBUG("D"), INFO("I"), WARNING("W"), ERROR("E");

        /**
         * Tag used to identify priority of logs in file.
         */
        private String mFileTag;

        private LogLevel(String fileTag) {
            mFileTag = fileTag;
        }

        public String getFileTag() {
            return mFileTag;
        }
    }

    /**
     * Class used for configuration of {@link LogWrapper}. It configures the default TAG, log dir
     * and file.
     *
     * @author Vinay Choudhary
     */
    public static class LogConfig {

        public static class LogConfigAlreadySetException extends Exception {

            private static final long serialVersionUID = -640876501836656739L;

            private LogConfigAlreadySetException(String message) {
                super(message);
            }
        }

        private static final int CALLER_METHOD_IDX_IN_STACK = 3;

        private String mDefTag = DEFAULT_TAG;
        private String mDefFileName = DEFAULT_FILE;
        private String mDefDirName = DEFAULT_DIR;
        private long mLogFileSize = MAX_LOG_FILE_SIZE;
        private boolean mIsConfigSet = false;
        private boolean mLoggingEnabled = true;
        private boolean mFileLoggingEnabled = true;
        private LogLevel mLogLevel = LogLevel.VERBOSE;
        private LogLevel mFileLogLevel = LogLevel.VERBOSE;
        private boolean mLookInStackForMethodName = false;

        /**
         * Configures the {@link LogWrapper}. Can only be configured once in an application. No
         * validation is done to check if filename and directory string being passed is valid or
         * not! <br>
         * <br>
         * Should be configured before using {@link LogWrapper}, so all {@link LogWrapper} objects
         * have same configuration. Not mandatory to configure in which case DEFAULTS are used.
         *
         * @param defTag the default Tag. If null, {@value LogWrapper#DEFAULT_TAG} is used.
         * @param defDirName the default Log Directory. If null, {@value LogWrapper#DEFAULT_DIR} is
         *            used.
         * @param defFileName the default Log File. If null, {@value LogWrapper#DEFAULT_FILE} is
         *            used.
         * @throws LogConfigAlreadySetException thrown when configuration is being set more than
         *             once in application
         */
        public synchronized void setConfig(String defTag, String defDirName, String defFileName,
                long fileSize)
                throws LogConfigAlreadySetException {
            if (!mIsConfigSet) {

                if (defDirName != null) {
                    mDefDirName = defDirName;
                }
                if (defFileName != null) {
                    mDefFileName = defFileName;
                }
                if (mDefTag != null) {
                    mDefTag = defTag;
                }

                if (fileSize > MIN_LOG_FILE_SIZE && fileSize < MAX_LOG_FILE_SIZE) {
                    mLogFileSize = fileSize;
                }

                mIsConfigSet = true;
                Log.v(mDefTag, "LogWrapper is configured with DEF_TAG: " + mDefTag + ", DEF_DIR: "
                        + mDefDirName + ", DEF_FILE: " + mDefFileName + ", FILE_SIZE_MAX: "
                        + mLogFileSize);
            }
            else {
                throw new LogConfigAlreadySetException("Log Configuration has already been set.");
            }
        }

        /**
         * Enable or disable all logging. This is master flag and overrides individual TAG based
         * logging. If disabled no logs will work, if enabled TAG logging will work according to
         * individual TAG setting.
         *
         * @param enableLogCatLogs <i><b>true</b></i> for enabling LogCat logs, <i><b>false</b></i>
         *            for disabling LogCat logs.
         * @param enableFileLogs <i><b>true</b></i> for enabling file logging, <i><b>false</b></i>
         *            for disabling file logging.
         */
        public void setGlobalLoggingEnabled(boolean enableLogCatLogs, boolean enableFileLogs) {
            mLoggingEnabled = enableLogCatLogs;
            mFileLoggingEnabled = enableFileLogs;
        }

        /**
         * Set Global log level. Applies to all tags and takes priority over TAG based log priority.
         * If TAG priority is set lower and global priority is higher then TAG logs with lower
         * priority will not be shown.
         *
         * @param logCatLogLevel {@link LogLevel} for logging in LogCat.
         * @param fileLogLevel {@link LogLevel} for logging in file.
         */
        public void setGlobalLogLevel(LogLevel logCatLogLevel, LogLevel fileLogLevel) {
            if (logCatLogLevel != null) {
                mLogLevel = logCatLogLevel;
            }
            if (fileLogLevel != null) {
                mFileLogLevel = fileLogLevel;
            }
        }

        /**
         * Whether search for method name in stack trace or use an predefined index. Using
         * predefined index avoid searching for method name but may vary based on Java/Android
         * runtime. Index is set to {@value #CALLER_METHOD_IDX_IN_STACK}.
         *
         * @param lookInStack if true method name is searched else predefined index is used.
         * @see LogWrapper#logMethodEntry()
         * @see LogWrapper#logMethodExit()
         */
        public void findMethodNameInStack(boolean lookInStack) {
            mLookInStackForMethodName = lookInStack;
        }
    }

    private String mTag;
    private String mDirPath;
    private String mFileName;
    private File mExtStrPath = Environment.getExternalStorageDirectory();
    private File mLogFile;
    private File mLogDir;
    private boolean mCanWriteFile;
    private boolean mEnableLogs = true;
    private boolean mEnableFileLogs = true;
    private LogLevel mLogLevel = LogLevel.VERBOSE;
    private LogLevel mFileLogLevel = LogLevel.VERBOSE;

    private static volatile HashMap<String, LogWrapper> sLoggers = new HashMap<String, LogWrapper>();
    private static volatile HashMap<String, Integer> sLogRefs = new HashMap<String, Integer>();

    private static final LogConfig sConfig = new LogConfig();

    private LogWrapper(String tag) {
        mTag = tag == null ? sConfig.mDefTag : tag;
        mDirPath = mExtStrPath.getAbsolutePath() + "/" + sConfig.mDefDirName;
        mFileName = sConfig.mDefFileName;

        initFile();

        // logInfo("Created LogWrapper");
    }

    private LogWrapper(String tag, String dirPath, String fileName) {
        mTag = tag == null ? sConfig.mDefTag : tag;
        mFileName = fileName == null ? mTag + ".log" : sConfig.mDefFileName;
        if (dirPath == null) {
            mDirPath = mExtStrPath.getAbsolutePath() + "/" + sConfig.mDefDirName;
        }

        initFile();
    }

    private void initFile() {
        synchronized (this) {
            mLogDir = new File(mDirPath);
            mLogFile = new File(mDirPath + "/" + mFileName);

            mCanWriteFile = mLogDir.mkdirs() || mLogDir.isDirectory();
            if (!mCanWriteFile) {
                return;
            }

            try {
                if (!mLogFile.exists()) {
                    mLogFile.createNewFile();
                    mCanWriteFile = true;
                    logInfo("Initializing log file: " + mLogFile.getAbsolutePath());
                }
                else if (mLogFile.length() > sConfig.mLogFileSize) {
                    logInfo("Deleting old log file (" + mLogFile.getAbsolutePath()
                            + ") as it exceeds " + sConfig.mLogFileSize + " bytes.");
                    mLogFile.delete();
                    mLogFile.createNewFile();
                    mCanWriteFile = true;
                    logInfo("Initializing log file: " + mLogFile.getAbsolutePath());
                }
            } catch (IOException e) {
                mCanWriteFile = false;
                logExceptionError(e);
            }
        }
    }

    /**
     * Get the logger object based on requested tag. File logging is generic and all logs go in same
     * file (externalDirectory/{@value #DEFAULT_FILE}. Same logger will be used for logs with same
     * tags. This is to avoid creation of multiple {@link LogWrapper} with same tag. Must call
     * {@link #releaseLogger(String)} when logging is not required any more. <br>
     * <br>
     * Configure {@link LogWrapper} before getting any instance at startup of application as shown
     * in example below:
     *
     * <pre>
     * static {
     *     LogConfig logConfig = LogWrapper.getConfigurator();
     *     try {
     *         logConfig.setConfig(FotaConfig.BASE_TAG, FotaConfig.LOG_DIR, FotaConfig.LOG_FILE,
     *                 FotaConfig.LOG_FILE_SIZE);
     *     } catch (LogConfigAlreadySetException e) {
     *         Log.v(FotaConfig.BASE_TAG, &quot;Failed to configure LogWrapper. Error: &quot; + e.getMessage());
     *     }
     * }
     *
     * </pre>
     *
     * @param tag The requested tag used for logging
     * @return {@link LogWrapper} object
     */
    public static LogWrapper getLogger(String tag) {
        if (tag == null) {
            tag = sConfig.mDefTag;
        }

        LogWrapper logger = null;

        synchronized (LogWrapper.class) {
            logger = sLoggers.get(tag);
            if (logger == null) {
                logger = new LogWrapper(tag);
                sLoggers.put(tag, logger);
            }

            Integer count = sLogRefs.get(tag);

            // count will be null when logger is created for a tag for first time.
            count = count == null ? 0 : count;
            sLogRefs.put(tag, ++count);
        }

        // Log.i(DEFAULT_TAG, "Returning logwrapper for: " + logger.mTag);
        return logger;
    }

    /**
     * Get the logger object based on requested tag. Any file logging will be unique for this tag.
     * Same logger will be used for logs with same tags. This is to avoid creation of multiple
     * {@link LogWrapper} with same tag. Must call {@link #releaseLogger(String)} when logging is
     * not required any more. <br>
     * <br>
     * Configure {@link LogWrapper} before getting any instance at startup of application as shown
     * in example below:
     *
     * <pre>
     * static {
     *     LogConfig logConfig = LogWrapper.getConfigurator();
     *     try {
     *         logConfig.setConfig(FotaConfig.BASE_TAG, FotaConfig.LOG_DIR, FotaConfig.LOG_FILE,
     *                 FotaConfig.LOG_FILE_SIZE);
     *     } catch (LogConfigAlreadySetException e) {
     *         Log.v(FotaConfig.BASE_TAG, &quot;Failed to configure LogWrapper. Error: &quot; + e.getMessage());
     *     }
     * }
     *
     * </pre>
     *
     * @param tag The requested tag used for logging
     * @param dirPath directory path without trailing "/", if null external storage is used and log
     *            directory named {@value #DEFAULT_DIR} is created at root of external storage.
     * @param fileName the file name, if null "[TAG].log" is used.
     * @return {@link LogWrapper} object
     */
    public static LogWrapper getLogger(String tag, String dirPath, String fileName) {
        if (tag == null) {
            tag = sConfig.mDefTag;
        }

        LogWrapper logger = null;
        synchronized (LogWrapper.class) {
            logger = sLoggers.get(tag);
            if (logger == null) {
                logger = new LogWrapper(tag, dirPath, fileName);
                sLoggers.put(tag, logger);
            }

            Integer count = sLogRefs.get(tag);

            // count will be null when logger is created for a tag for first time.
            count = count == null ? 0 : count;
            sLogRefs.put(tag, ++count);
        }

        return logger;
    }

    /**
     * Release the logger object based on requested tag.
     *
     * @param tag The requested tag used for logging, If null, it is assumed that {@link LogWrapper}
     *            instance that caller is holding, was also received by passing null tag.
     * @return {@link LogWrapper} object
     */
    public static void releaseLogger(String tag) {

        if (tag == null) {
            tag = sConfig.mDefTag;
        }

        synchronized (LogWrapper.class) {
            LogWrapper logger = sLoggers.get(tag);
            if (logger == null) {
                // Count will not be null, if tag has an logger in map.
                Integer count = sLogRefs.get(tag);

                if (count == 1) {
                    // release instance
                    logger = null;
                    sLoggers.put(tag, null);
                }
                sLogRefs.put(tag, --count);
            }
        }
    }

    public static synchronized LogConfig getConfigurator() {
        return sConfig;
    }

    /**
     * Enable or disable logging for this TAG. This is TAG based flag and is overridden by global
     * flag.
     *
     * @param enableLogCatLogs <i><b>true</b></i> for enabling LogCat logs, <i><b>false</b></i> for
     *            disabling LogCat logs.
     * @param enableFileLogs <i><b>true</b></i> for enabling file logging, <i><b>false</b></i> for
     *            disabling file logging.
     * @see LogConfig#setGlobalLoggingEnabled(boolean, boolean)
     */
    public synchronized void setLoggingEnabled(boolean enableLogCatLogs, boolean enableFileLogs) {
        mEnableLogs = enableLogCatLogs;
        mEnableFileLogs = enableFileLogs;
    }

    /**
     * Set TAG log level. Global TAG takes priority over TAG based log priority. If TAG priority is
     * set lower and global priority is higher then only TAG logs with equal or higher priority will
     * be shown.
     *
     * @param logCatLogLevel {@link LogLevel} for logging in LogCat.
     * @param fileLogLevel {@link LogLevel} for logging in file.
     * @see LogConfig#setGlobalLogLevel(LogLevel, LogLevel)
     */
    public synchronized void setGlobalLogLevel(LogLevel logCatLogLevel, LogLevel fileLogLevel) {
        if (logCatLogLevel != null) {
            mLogLevel = logCatLogLevel;
        }
        if (fileLogLevel != null) {
            mFileLogLevel = fileLogLevel;
        }
    }

    /**
     * Logs the method entry as "---> methodName()". It has Debug level priority.
     *
     * @param methodName the methodName
     * @see #logMethodEntry()
     */
    public void logMethodEntry(String methodName) {
        if (mEnableLogs && sConfig.mLoggingEnabled) {
            logDebug("---> " + methodName + "()");
        }
    }

    /**
     * Logs the method entry as "---> caller()". This is a bit slower than
     * {@link #logMethodEntry(String)}. It has Debug level priority.
     */
    public void logMethodEntry() {
        String methodName = null;
        if (mEnableLogs && sConfig.mLoggingEnabled) {
            if (sConfig.mLookInStackForMethodName) {
                int i = 0;
                for (StackTraceElement s : Thread.currentThread().getStackTrace()) {
                    i++;
                    if (s.getMethodName().equals("logMethodEntry")) {
                        break;
                    }
                }
                methodName = Thread.currentThread().getStackTrace()[i].getMethodName();
            } else {
                methodName = Thread.currentThread().getStackTrace()[LogConfig.CALLER_METHOD_IDX_IN_STACK]
                        .getMethodName();
            }
            logDebug("---> " + methodName + "()");
        }
    }

    /**
     * Logs the method exit as "<--- methodName()". It has Debug level priority.
     *
     * @param methodName the methodName
     * @see #logMethodExit()
     */
    public void logMethodExit(String methodName) {
        if (mEnableLogs && sConfig.mLoggingEnabled) {
            logDebug("<--- " + methodName + "()");
        }
    }

    /**
     * Logs the method exit as "<--- caller()". This is a bit slower than
     * {@link #logMethodExit(String)}. It has Debug level priority.
     */
    public void logMethodExit() {
        String methodName = null;
        if (mEnableLogs && sConfig.mLoggingEnabled) {
            if (sConfig.mLookInStackForMethodName) {
                int i = 0;
                for (StackTraceElement s : Thread.currentThread().getStackTrace()) {
                    i++;
                    if (s.getMethodName().equals("logMethodExit")) {
                        break;
                    }
                }
                methodName = Thread.currentThread().getStackTrace()[i].getMethodName();
            } else {
                methodName = Thread.currentThread().getStackTrace()[LogConfig.CALLER_METHOD_IDX_IN_STACK]
                        .getMethodName();
            }
            logDebug("<--- " + methodName + "()");
        }
    }

    /**
     * Logs the error message.
     *
     * @param msg the message
     */
    public void logError(String msg) {
        if (canLogAtLevel(LogLevel.ERROR)) {
            Log.e(mTag, msg);
        }
    }

    /**
     * Logs the warning message.
     *
     * @param msg the message
     */
    public void logWarning(String msg) {
        if (canLogAtLevel(LogLevel.WARNING)) {
            Log.w(mTag, msg);
        }
    }

    /**
     * Logs the info message.
     *
     * @param msg the message
     */
    public void logInfo(String msg) {
        if (canLogAtLevel(LogLevel.INFO)) {
            Log.i(mTag, msg);
        }
    }

    /**
     * Logs the debug message.
     *
     * @param msg the message
     */
    public void logDebug(String msg) {
        if (canLogAtLevel(LogLevel.DEBUG)) {
            Log.d(mTag, msg);
        }
    }

    /**
     * Logs the verbose message.
     *
     * @param msg the message
     */
    public void logVerbose(String msg) {
        if (canLogAtLevel(LogLevel.VERBOSE)) {
            Log.v(mTag, msg);
        }
    }

    /**
     * Logs only the human readable exception message.
     *
     * @param exp the exception
     */
    public void logExceptionError(Throwable e) {
        logError(e.toString());
    }

    /**
     * Logs the stack trace for the exception.
     *
     * @param exp the exception that occurred.
     */
    public void logStackTrace(Throwable e) {
        logError(getStackTace(e));
    }

    private String getStackTace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    private boolean canLogAtLevel(LogLevel level) {
        if (mEnableLogs && sConfig.mLoggingEnabled) {
            if (sConfig.mLogLevel.ordinal() >= level.ordinal()
                    && mLogLevel.ordinal() >= level.ordinal())
                return true;
        }
        return false;
    }

    /**
     * Write an info message in file associated with this LogWrapper. Also logs message.
     *
     * @param msg the message
     */
    public void writeInfo(String msg) {
        logInfo(msg);
        synchronized (this) {
            writeToFile(msg, LogLevel.INFO);
        }
    }

    /**
     * Write a warning message in file associated with this LogWrapper. Also logs message.
     *
     * @param msg the message
     */
    public void writeWarning(String msg) {
        logWarning(msg);
        synchronized (this) {
            writeToFile(msg, LogLevel.WARNING);
        }
    }

    /**
     * Write a debug message in file associated with this LogWrapper. Also logs message.
     *
     * @param msg the message
     */
    public void writeDebug(String msg) {
        logDebug(msg);
        synchronized (this) {
            writeToFile(msg, LogLevel.DEBUG);
        }
    }

    /**
     * Write an error message in file associated with this LogWrapper. Also logs message.
     *
     * @param msg the message
     */
    public void writeError(String msg) {
        logError(msg);
        synchronized (this) {
            writeToFile(msg, LogLevel.ERROR);
        }
    }

    /**
     * Write a verbose message in file associated with this LogWrapper. Also logs message.
     *
     * @param msg the message
     */
    public void writeVerbose(String msg) {
        logVerbose(msg);
        synchronized (this) {
            writeToFile(msg, LogLevel.VERBOSE);
        }
    }

    /**
     * Write an exception message in file associated with this LogWrapper. Also logs exception
     * message.
     *
     * @param e the Exception
     */
    public void writeException(Throwable e) {
        logExceptionError(e);
        synchronized (mTag) {
            writeToFile(e.toString(), LogLevel.ERROR);
        }
    }

    /**
     * Write the exception stack trace in file associated with this LogWrapper. Also logs stack
     * trace.
     *
     * @param e the Exception
     */
    public void writeStackTrace(Throwable e) {
        logStackTrace(e);
        synchronized (mTag) {
            writeToFile(getStackTace(e), LogLevel.ERROR);
        }
    }

    private void writeToFile(String msg, LogLevel level) {
        if (!mCanWriteFile || !mEnableFileLogs || !sConfig.mFileLoggingEnabled) {
            return;
        }

        if (sConfig.mFileLogLevel.ordinal() >= level.ordinal()
                && mFileLogLevel.ordinal() >= level.ordinal()) {
            return;
        }

        FileWriter fOut = null;
        BufferedWriter myOutWriter = null;
        try {
            Timestamp t = new Timestamp(System.currentTimeMillis());
            String log = String.format("%s %s/%s(%d): %s\n", t.toString(), level.getFileTag(),
                    mTag, Process.myTid(), msg);
            fOut = new FileWriter(mLogFile, true);
            myOutWriter = new BufferedWriter(fOut);
            myOutWriter.write(log);
            myOutWriter.newLine();
            myOutWriter.close();
        } catch (FileNotFoundException e) {
            logExceptionError(e);
        } catch (IOException e) {
            logExceptionError(e);
        }
    }
}
