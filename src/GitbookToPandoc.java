import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

/**
 * Takes a Gitbook directory and a output directory, and converts all markdowns
 * inside the Gitbook directory into LaTeX, and creates a master book.tex that
 * includes all the converted latex files. Essentially, a book.
 * 
 * @author linanqiu
 * @file_name GitbookToPandoc.java
 */
/**
 * @author linanqiu
 * @file_name GitbookToPandoc.java
 */
public class GitbookToPandoc {

	public static final int CHAPTER = 1;
	public static final int SUBCHAPTER = 2;
	public static final String PANDOCPATH = "/usr/local/bin/pandoc";

	private String in_directory;
	private String out_directory;
	private String header;

	private LinkedHashMap<File, Integer> index;

	private File summary;

	/**
	 * The class that does most of the grunt work
	 * 
	 * @param in_directory
	 *            directory that contains 'summary.md'. The original gitbook
	 *            directory
	 * @param out_directory
	 *            the output directory where all contents of in_directory will
	 *            be copied to, then converted, then new book.tex created
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public GitbookToPandoc(String in_directory, String out_directory)
			throws IOException, InterruptedException {
		this.in_directory = in_directory;
		this.out_directory = out_directory;
		index = new LinkedHashMap<File, Integer>();

		// read in the header file
		try {
			header = FileUtils
					.readFileToString(new File("header.tex"), "ASCII");
		} catch (IOException e) {
			System.out.println("Missing header.tex");
			e.printStackTrace();
		}

		// copy the source to destination
		copyFolder(new File(in_directory), new File(out_directory));

		// find the summary file in the source folder
		findSummary();

		// add in the extra README.md from the gitbook folder itself (usually
		// serves as introduction or foreword or whatever
		buildForeword();

		// indexes all the markdown files based on the summary.md
		buildIndex();

		// converts markdown files to LaTeX using pandoc
		markdownToLatex();

		// outputs LaTeX file
		outputLatex();
	}

	/**
	 * Finds the summary.md file in the gitbook directory. Ignores case.
	 */
	private void findSummary() {

		File out_dir = new File(out_directory);
		File[] listOfFiles = out_dir.listFiles();

		for (File file : listOfFiles) {
			if (file.getName().equalsIgnoreCase("summary.md")) {
				summary = file;
			}
		}

	}

	/**
	 * Builds a LinkedHashMap of markdown files from the summary.md by
	 * extracting the relative file paths from between the () brackets in
	 * summary.md. Places a File class wrapper on each of these for convenience
	 * later.
	 * 
	 * @throws IOException
	 */
	private void buildIndex() throws IOException {

		String summaryString = "";
		Scanner scan = new Scanner(new FileReader(summary));
		while (scan.hasNext()) {
			summaryString += (scan.nextLine() + "\n");
		}
		Pattern pattern = Pattern.compile("[(](.*)[)]");
		Matcher matcher = pattern.matcher(summaryString);
		while (matcher.find()) {
			File markdownFile = new File(out_directory + matcher.group(1));

			if (matcher.group().toLowerCase().indexOf("readme") > -1) {
				index.put(markdownFile, CHAPTER);
			} else {
				index.put(markdownFile, SUBCHAPTER);
			}
		}
	}

	/**
	 * Converts each of these markdown files into LaTeX using pandoc. Assumes
	 * that the directory pandoc resides in is /usr/local/bin/pandoc. To
	 * override that, change the static declaration at the top.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void markdownToLatex() throws IOException, InterruptedException {

		for (File markdown : index.keySet()) {
			superscriptSubscript(markdown);

			Runtime r = Runtime.getRuntime();
			String[] command = new String[] { PANDOCPATH, "-o",
					markdown.getAbsolutePath().replaceAll(".md", ".tex"),
					markdown.getAbsolutePath() };

			for (String param : command) {
				System.out.print(param + " ");
			}
			System.out.println();

			Process p = r.exec(command);

			BufferedReader b = new BufferedReader(new InputStreamReader(
					p.getInputStream()));
			String line = "";

			while ((line = b.readLine()) != null) {
				System.out.println(line);
			}
			p.waitFor();
		}

	}

	/**
	 * Now the tricky thing about gitbook is that there is a readme.md in the
	 * gitbook directory itself (not as any chapter). It is sort of like an
	 * introduction. A chapter 0. We extract that markdown file and add that to
	 * the top of our book.
	 * 
	 * @throws IOException
	 */
	private void buildForeword() throws IOException {

		File out_dir = new File(out_directory);
		File[] listOfFiles = out_dir.listFiles();

		for (File file : listOfFiles) {
			if (file.getName().equalsIgnoreCase("readme.md")) {
				index.put(file, CHAPTER);
			}
		}

	}

	/**
	 * Output the book.tex file that references each of the converted markdown
	 * files by using \include{filename} in LaTex
	 * 
	 * @throws IOException
	 */
	private void outputLatex() throws IOException {
		File latex = new File(out_directory + "book.tex");
		FileWriter writer = new FileWriter(latex);

		String includes = "";

		for (File markdown : index.keySet()) {
			File converted = new File(markdown.getAbsolutePath().replaceAll(
					".md", ".tex"));
			if (index.get(markdown) == SUBCHAPTER) {
				shift(converted);
			}
			
			// make relative paths
			String path = converted.getAbsolutePath();
			String base = new File(out_directory).getAbsolutePath();
			String relative = new File(base).toURI()
					.relativize(new File(path).toURI()).getPath();

			includes = includes + "\\\\include{"
					+ relative.replaceAll(".tex", "") + "}" + "\n";
		}

		header = header.replaceAll("<CONTENT>", includes);

		writer.write(header);

		writer.close();
	}

	/**
	 * Now gitbook demands that even subchapters are titled using #Title (H1),
	 * hence if we convert naively using pandoc, each subchapter will become
	 * \section, which is screwed up. So we have to push each section in the
	 * subchapters down by one. We do that by replacing section{ with
	 * subsection{
	 * 
	 * @param converted
	 * @throws IOException
	 */
	private void shift(File converted) throws IOException {
		String file = FileUtils.readFileToString(converted, "ASCII");
		file = file.replaceAll("section\\{", "subsection\\{");
		FileWriter writer = new FileWriter(converted);
		writer.write(file);
		writer.close();
	}

	/**
	 * Gitbook and pandoc handles superscripts and subscripts differently (this
	 * is mainly for my own project). While Gitbook demands <sub>lorem</sub> as
	 * subscripts, Pandoc takes only ~lorem~. Hence, we replace accordingly.
	 * Same for superscript.
	 * 
	 * @param markdown
	 * @throws IOException
	 */
	private void superscriptSubscript(File markdown) throws IOException {
		String file = FileUtils.readFileToString(markdown, "ASCII");
		file = file.replaceAll("<sub>", "~");
		file = file.replaceAll("</sub>", "~");
		file = file.replaceAll("<sup>", "^");
		file = file.replaceAll("</sup>", "^");

		FileWriter writer = new FileWriter(markdown);
		writer.write(file);
		writer.close();
	}

	/**
	 * Function stolen online to quickly copy directories.
	 * 
	 * @param src
	 *            source directory
	 * @param dest
	 *            destination directory
	 * @throws IOException
	 */
	private void copyFolder(File src, File dest) throws IOException {

		if (src.isDirectory()) {

			// if directory not exists, create it
			if (!dest.exists()) {
				dest.mkdir();
			}

			// list all the directory contents
			String files[] = src.list();

			for (String file : files) {
				// construct the src and dest file structure
				File srcFile = new File(src, file);
				File destFile = new File(dest, file);
				// recursive copy
				copyFolder(srcFile, destFile);
			}

		} else {
			// if file, then copy it
			// Use bytes stream to support all file types
			InputStream in = new FileInputStream(src);
			OutputStream out = new FileOutputStream(dest);

			byte[] buffer = new byte[1024];

			int length;
			// copy the file content in bytes
			while ((length = in.read(buffer)) > 0) {
				out.write(buffer, 0, length);
			}

			in.close();
			out.close();
		}
	}

	/**
	 * Execute GitbookToPandoc
	 * 
	 * @param args
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws IOException,
			InterruptedException {

		if (args.length != 2) {
			System.out
					.println("Usage: java GitbookToPandoc sourcefolder destinationfolder");
			System.exit(1);
		}

		String in_directory = args[0];
		String out_directory = args[1];

		// adds slash behind directory
		if (in_directory.charAt(in_directory.length() - 1) != '/') {
			in_directory = in_directory + '/';
		}
		if (out_directory.charAt(out_directory.length() - 1) != '/') {
			out_directory = out_directory + '/';
		}

		GitbookToPandoc test = new GitbookToPandoc(in_directory, out_directory);
	}
}
