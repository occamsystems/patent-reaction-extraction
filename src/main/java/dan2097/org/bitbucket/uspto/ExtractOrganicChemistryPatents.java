package dan2097.org.bitbucket.uspto;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Nodes;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import dan2097.org.bitbucket.utility.StringUtils;
import dan2097.org.bitbucket.utility.Utils;

public class ExtractOrganicChemistryPatents {
	private static Logger LOG = Logger.getLogger(ExtractOrganicChemistryPatents.class);
	
	static{
		LOG.setLevel(Level.DEBUG);
	}
	
	private final File inputDirectory;
	private final File outputDirectory;
	
	/**
	 * An input directory in which zip/tar files from
	 * http://www.google.com/googlebooks/uspto-patents-applications-text-with-embedded-images.html
	 * are placed and an output location for appropriate patents
	 * @param inputDirectoryLocation
	 * @param outputDirectoryLocation
	 * @throws IOException 
	 */
	public ExtractOrganicChemistryPatents(String inputDirectoryLocation, String outputDirectoryLocation) throws IOException {
		this(new File(inputDirectoryLocation), new File(outputDirectoryLocation));
	}
	
	/**
	 * An input directory in which zip/tar files from
	 * http://www.google.com/googlebooks/uspto-patents-applications-text-with-embedded-images.html
	 * are placed and an output location for appropriate patents
	 * @param inputDirectory
	 * @param outputDirectory
	 * @throws IOException 
	 */
	public ExtractOrganicChemistryPatents(File inputDirectory, File outputDirectory) throws IOException {
		checkInputs(inputDirectory, outputDirectory);
		FileUtils.forceMkdir(outputDirectory);
		this.inputDirectory = inputDirectory;
		this.outputDirectory = outputDirectory;
	}

	public void extractOrganicPatents() throws IOException{
		Iterator<File> fileIterator = FileUtils.iterateFiles(inputDirectory, new String[]{"ZIP", "zip", "Zip", "TAR", "tar", "Tar"}, false);
		while (fileIterator.hasNext()) {
			processPatentArchiveFile((File) fileIterator.next());
		}
		File tempDirectory = new File(outputDirectory.getAbsolutePath() +"/temp");
		if(tempDirectory.exists()){
			FileUtils.forceDeleteOnExit(tempDirectory);
		}
	}
	private void processPatentArchiveFile(File patentArchiveFile) throws IOException {
		LOG.debug("Processing: " + patentArchiveFile.getAbsolutePath());
		File tempDirectory = new File(outputDirectory.getAbsolutePath() +"/temp/" + patentArchiveFile.getName());
		FileUtils.forceMkdir(tempDirectory);
		File archiveOutputDirectory = new File(outputDirectory.getAbsolutePath() +"/" + patentArchiveFile.getName());
		FileUtils.forceMkdir(archiveOutputDirectory);
		if (StringUtils.endsWithCaseInsensitive(patentArchiveFile.getName(), "zip")){
			extractFilesFromZipFile(patentArchiveFile, tempDirectory);
		}
		else if (StringUtils.endsWithCaseInsensitive(patentArchiveFile.getName(), "tar")){
			extractCandidateZipFilesFromTarFile(patentArchiveFile, tempDirectory);
		}
		else{
			throw new RuntimeException("Unexpected file extension: " + patentArchiveFile.getName());
		}
		processZipFiles(tempDirectory, archiveOutputDirectory);
		FileUtils.forceDeleteOnExit(tempDirectory);
	}

	/**
	 * Extracts all zip files present within the given zip file
	 * @param patentArchiveFile
	 * @param tempDirectory
	 * @throws IOException
	 */
	private void extractFilesFromZipFile(File patentArchiveFile, File tempDirectory) throws IOException {
		ZipFile zipFile = new ZipFile(patentArchiveFile);
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while(entries.hasMoreElements()) {
			ZipEntry zipEntry = entries.nextElement();
			if(StringUtils.endsWithCaseInsensitive(zipEntry.getName(), "xml")){
				InputStream inputStream =  zipFile.getInputStream(zipEntry);
				File f = new File(tempDirectory +"/" + FilenameUtils.getName(zipEntry.getName()));
				FileOutputStream fos = new FileOutputStream(f);
				IOUtils.copy(inputStream, fos);
				IOUtils.closeQuietly(fos);
			}
		}
		zipFile.close();
	}
	
	/**
	 * Extracts all zip files present within the given tar file
	 * @param patentArchiveFile
	 * @param tempDirectory
	 * @throws IOException
	 */
	private void extractCandidateZipFilesFromTarFile(File patentArchiveFile, File tempDirectory) throws IOException {
		TarArchiveInputStream tin = new TarArchiveInputStream(new FileInputStream(patentArchiveFile));
		TarArchiveEntry tarEntry = tin.getNextTarEntry();
		while(tarEntry !=null){
			if(StringUtils.endsWithCaseInsensitive(tarEntry.getName(), "zip")){
				File f = new File(tempDirectory +"/" + FilenameUtils.getName(tarEntry.getName()));
				FileOutputStream fos = new FileOutputStream(f);
				IOUtils.copy(tin, fos);
				IOUtils.closeQuietly(fos);
			}
			tarEntry = tin.getNextTarEntry();
		}
	}

	private void processZipFiles(File inputDirectory, File archiveOutputDirectory) throws ZipException, IOException {
		Iterator<File> fileIterator = FileUtils.iterateFiles(inputDirectory, new String[]{"xml"}, false);
		FileUtils.forceMkdir(new File(archiveOutputDirectory + "/temp/"));
		while (fileIterator.hasNext()) {
			File f = (File) fileIterator.next();
			FileReader fileReader = new FileReader(f);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			int i = 0;
			String line = bufferedReader.readLine();
			File currentOutputFile = null;
			FileWriter currentOutputWriter = null;
			while (line != null) {
				if (line.startsWith("<?xml ")) {
					if (currentOutputFile != null) {
						currentOutputWriter.close();
						InputStream inputStream = new FileInputStream(currentOutputFile);
						Document doc;
						try {
							LOG.debug("> Attempting to read document section file " + currentOutputFile.getName());
							doc = Utils.buildXmlFile(inputStream);
						} catch (Exception e) {
							LOG.fatal(f.getAbsolutePath());
							throw new RuntimeException("Failed to read document", e);
						}
						if (isOrganicChemistryDocument(doc)) {
							FileUtils.copyFile(f, new File(archiveOutputDirectory + "/" + currentOutputFile.getName()));
						}
					}

					currentOutputFile = new File(archiveOutputDirectory + "/temp/" + f.getName().split(".xml")[0] + "-" + i + ".xml");
					LOG.debug("Creating new file for subsection " + currentOutputFile.getName());
					currentOutputFile.deleteOnExit();
				  currentOutputWriter = new FileWriter(currentOutputFile);
					i++;
				}

				currentOutputWriter.write(line);
				line = bufferedReader.readLine();
			}

			if (currentOutputWriter != null) {
				currentOutputWriter.close();
			}

			bufferedReader.close();
		}
	}

	private boolean isOrganicChemistryDocument(Document doc) {
		LOG.debug(">> Classifying document contents");
		Nodes classifications = doc.query("//classification-ipcr");
		for (int i = 0; i < classifications.size(); i++) {
			Element classification = (Element) classifications.get(i);
			Element section = classification.getFirstChildElement("section");
			if (section != null && section.getValue().equalsIgnoreCase("C")){
				Element claz = classification.getFirstChildElement("class");
				if (claz != null && (claz.getValue().equalsIgnoreCase("07") || claz.getValue().equalsIgnoreCase("7"))){
					LOG.debug("<< Document contains organic chemistry section");
					return true;
				}
			}
		}
		LOG.debug("<< Document does not contain organic chemistry section");
		return false;
	}

	private void checkInputs(File in, File out) {
		if (!in.exists()){
			throw new IllegalArgumentException("input directory does not exist");
		}
		if (!in.isDirectory()){
			throw new IllegalArgumentException("input directory is not a directory");
		}
		if (out.exists() && !out.isDirectory()){
			throw new IllegalArgumentException("output directory is not a directory");
		}
	}
	
	
	public static void main(String[] args) throws IOException {
		String inputDirectory  = "inputs";
		String outputDirectory  = "outputs";
		ExtractOrganicChemistryPatents eocp= new ExtractOrganicChemistryPatents(inputDirectory, outputDirectory);
		eocp.extractOrganicPatents();
	}
}
