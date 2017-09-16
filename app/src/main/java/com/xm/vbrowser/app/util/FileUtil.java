package com.xm.vbrowser.app.util;



import android.util.Log;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.util.regex.Pattern;

/**
 * @author fisher
 * @description 文件工具类
 */

public class FileUtil {
    //private static final Log Debug = LogFactory.getLog(FileUtil.class);

    // 获取从classpath根目录开始读取文件注意转化成中文
    public static String getCPFile(String path) {
        URL url = FileUtil.class.getClassLoader().getResource(path);
        String filepath = url.getFile();
        File file = new File(filepath);
        byte[] retBuffer = new byte[(int) file.length()];
        try {
            FileInputStream fis = new FileInputStream(filepath);
            fis.read(retBuffer);
            fis.close();
            return new String(retBuffer, "GBK");
        } catch (IOException e) {
            //Debug.error("FileUtils.getCPFile读取文件异常：" + e.toString());
            return null;
        }
    }


    /**
     * 利用java本地拷贝文件及文件夹,如何实现文件夹对文件夹的拷贝呢?如果文件夹里还有文件夹怎么办呢?
     *
     * @param objDir
     *            目标文件夹
     * @param srcDir
     *            源的文件夹
     * @throws IOException
     */
    public static void copyDirectiory(String objDir, String srcDir)
            throws IOException {
        (new File(objDir)).mkdirs();
        File[] file = (new File(srcDir)).listFiles();
        for (int i = 0; i < file.length; i++) {
            if (file[i].isFile()) {
                FileInputStream input = new FileInputStream(file[i]);
                FileOutputStream output = new FileOutputStream(objDir + "/"
                        + file[i].getName());
                byte[] b = new byte[1024 * 5];
                int len;
                while ((len = input.read(b)) != -1) {
                    output.write(b, 0, len);
                }
                output.flush();
                output.close();
                input.close();
            }
            if (file[i].isDirectory()) {
                copyDirectiory(objDir + "/" + file[i].getName(), srcDir + "/"
                        + file[i].getName());
            }
        }
    }

    /**
     * 将一个文件inName拷贝到另外一个文件outName中
     *
     * @param inName
     *            源文件路径
     * @param outName
     *            目标文件路径
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static void copyFile(String inName, String outName)
            throws FileNotFoundException, IOException {
        BufferedInputStream is = new BufferedInputStream(new FileInputStream(
                inName));
        BufferedOutputStream os = new BufferedOutputStream(
                new FileOutputStream(outName));
        copyFile(is, os, true);
    }

    /**
     * Copy a file from an opened InputStream to opened OutputStream
     *
     * @param is
     *            source InputStream
     * @param os
     *            target OutputStream
     * @param close
     *            写入之后是否需要关闭OutputStream
     * @throws IOException
     */
    public static void copyFile(InputStream is, OutputStream os, boolean close)
            throws IOException {
        int b;
        while ((b = is.read()) != -1) {
            os.write(b);
        }
        is.close();
        if (close)
            os.close();
    }

    public static void copyFile(Reader is, Writer os, boolean close)
            throws IOException {
        int b;
        while ((b = is.read()) != -1) {
            os.write(b);
        }
        is.close();
        if (close)
            os.close();
    }

    public static void copyFile(String inName, PrintWriter pw, boolean close)
            throws FileNotFoundException, IOException {
        BufferedReader is = new BufferedReader(new FileReader(inName));
        copyFile(is, pw, close);
    }

    /**
     * 从文件inName中读取第一行的内容
     *
     * @param inName
     *            源文件路径
     * @return 第一行的内容
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static String readLine(String inName) throws FileNotFoundException,
            IOException {
        BufferedReader is = new BufferedReader(new FileReader(inName));
        String line = null;
        line = is.readLine();
        is.close();
        return line;
    }

    /**
     * default buffer size
     */
    private static final int BLKSIZ = 8192;

    public static void copyFileBuffered(String inName, String outName)
            throws FileNotFoundException, IOException {
        InputStream is = new FileInputStream(inName);
        OutputStream os = new FileOutputStream(outName);
        int count = 0;
        byte b[] = new byte[BLKSIZ];
        while ((count = is.read(b)) != -1) {
            os.write(b, 0, count);
        }
        is.close();
        os.close();
    }

    /**
     * 将String变成文本文件
     *
     * @param text
     *            源String
     * @param fileName
     *            目标文件路径
     * @throws IOException
     */
    public static void stringToFile(String text, String fileName)
            throws IOException {
        BufferedWriter os = new BufferedWriter(new FileWriter(fileName));
        os.write(text);
        os.flush();
        os.close();
    }

    /**
     * 打开文件获得BufferedReader
     *
     * @param fileName
     *            目标文件路径
     * @return BufferedReader
     * @throws IOException
     */
    public static BufferedReader openFile(String fileName) throws IOException {
        return new BufferedReader(new FileReader(fileName));
    }

    /**
     * 获取文件filePath的字节编码byte[]
     *
     * @param filePath
     *            文件全路径
     * @return 文件内容的字节编码
     * @roseuid 3FBE26DE027D
     */
    public static byte[] fileToBytes(String filePath) {
        if (filePath == null) {
            //Debug.info("路径为空：");
            return null;
        }

        File tmpFile = new File(filePath);

        byte[] retBuffer = new byte[(int) tmpFile.length()];
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(filePath);
            fis.read(retBuffer);
            fis.close();
            return retBuffer;
        } catch (IOException e) {
            //Debug.error("读取文件异常：" + e.toString());
            return null;
        }
    }

    /**
     * 将byte[]转化成文件fullFilePath
     *
     * @param fullFilePath
     *            文件全路径
     * @param content
     *            源byte[]
     */
    public static void bytesToFile(String fullFilePath, byte[] content) {
        if (fullFilePath == null || content == null) {
            return;
        }

        // 创建相应的目录
        File f = new File(getDir(fullFilePath));
        if (f == null || !f.exists()) {
            f.mkdirs();
        }

        try {
            FileOutputStream fos = new FileOutputStream(fullFilePath);
            fos.write(content);
            fos.close();
        } catch (Exception e) {
            //Debug.error("写入文件异常:" + e.toString());
        }
    }

    /**
     * 根据传入的文件全路径，返回文件所在路径
     *
     * @param fullPath
     *            文件全路径
     * @return 文件所在路径
     */
    public static String getDir(String fullPath) {
        int iPos1 = fullPath.lastIndexOf("/");
        int iPos2 = fullPath.lastIndexOf("\\");
        iPos1 = (iPos1 > iPos2 ? iPos1 : iPos2);
        return fullPath.substring(0, iPos1 + 1);
    }

    /**
     * 根据传入的文件全路径，返回文件全名（包括后缀名）
     *
     * @param fullPath
     *            文件全路径
     * @return 文件全名（包括后缀名）
     */
    public static String getFileName(String fullPath) {
        int iPos1 = fullPath.lastIndexOf("/");
        int iPos2 = fullPath.lastIndexOf("\\");
        iPos1 = (iPos1 > iPos2 ? iPos1 : iPos2);
        return fullPath.substring(iPos1 + 1);
    }

    /**
     * 获得文件名fileName中的后缀名
     *
     * @param fileName
     *            源文件名
     * @return String 后缀名
     */
    public static String getFileSuffix(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1,
                fileName.length());
    }

    /**
     * 根据传入的文件全名（包括后缀名）或者文件全路径返回文件名（没有后缀名）
     *
     * @param fullPath
     *            文件全名（包括后缀名）或者文件全路径
     * @return 文件名（没有后缀名）
     */
    public static String getPureFileName(String fullPath) {
        String fileFullName = getFileName(fullPath);
        return fileFullName.substring(0, fileFullName.lastIndexOf("."));
    }

    /**
     * 转换文件路径中的\\为/
     *
     * @param filePath
     *            要转换的文件路径
     * @return String
     */
    public static String wrapFilePath(String filePath) {
        filePath.replace('\\', '/');
        if (filePath.charAt(filePath.length() - 1) != '/') {
            filePath += "/";
        }
        return filePath;
    }

    /**
     * 删除整个目录path,包括该目录下所有的子目录和文件
     *
     * @param path
     */
    public static void deleteDirs(String path) {
        File rootFile = new File(path);
        File[] files = rootFile.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                deleteDirs(file.getPath());
            } else {
                file.delete();
            }
        }
        rootFile.delete();
    }

    /** *//**文件重命名
     * @param path 文件目录
     * @param oldname  原来的文件名
     * @param newname 新文件名
     */
    public static void renameFile(String path,String oldname,String newname){
        if(!oldname.equals(newname)){//新的文件名和以前文件名不同时,才有必要进行重命名
            File oldfile=new File(path+File.separator+oldname);
            File newfile=new File(path+File.separator+newname);
            if(!oldfile.exists()){
                return;//重命名文件不存在
            }
            if(newfile.exists())//若在该目录下已经有一个文件和新文件名相同，则不允许重命名
                System.out.println(newname+"已经存在！");
            else{
                oldfile.renameTo(newfile);
            }
        }else{
            System.out.println("新文件名和旧文件名相同...");
        }
    }


    /** *//**文件夹重命名
     */
    public static boolean renameDir(String oldDirPath,String newDirPath){
        File oleFile = new File(oldDirPath); //要重命名的文件或文件夹
        File newFile = new File(newDirPath);  //重命名为zhidian1
        return oleFile.renameTo(newFile);  //执行重命名
    }

    public static String fileToString(String filePath){
        StringBuilder fileString = null;
        try {
            BufferedReader os = new BufferedReader(new FileReader(filePath));
            fileString = new StringBuilder();
            String valueString;
            while ((valueString = os.readLine()) != null) {
                fileString.append(valueString);
            }
            os.close();
        }catch (IOException e){
            Log.d("FileUtil", "文件异常", e);
        }
        return fileString == null? null:fileString.toString();
    }

    public static String getFormatedFileSize(long size){
        String result;
        if(size==0){
            return "0B";
        }
        DecimalFormat df = new DecimalFormat("#.00");
        if (size < 1024) {
            result = df.format((double) size) + "B";
        } else if (size < 1048576) {
            result = df.format((double) size / 1024) + "KB";
        } else if (size < 1073741824) {
            result = df.format((double) size / 1048576) + "MB";
        } else {
            result = df.format((double) size / 1073741824) +"GB";
        }
        return result;
    }

    public static String getExtension(String fileName){
        if(fileName.lastIndexOf(".")<1){
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".")+1);
    }


    public static String getName(String fileName){
        if(fileName.lastIndexOf(".")<1){
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    public static long getFolderSize(File file){
        long size = 0;
        File[] fileList = file.listFiles();
        for (File aFileList : fileList) {
            if (aFileList.isDirectory()) {
                size = size + getFolderSize(aFileList);
            } else {
                size = size + aFileList.length();
            }
        }
        return size;
    }

    public static String fileNameFilter(String fileName){
        Pattern FilePattern = Pattern.compile("[\\\\/:*?\"<>|.]");
        return fileName==null?null:FilePattern.matcher(fileName).replaceAll("");
    }
}
