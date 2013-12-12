/**
* OLAT - Online Learning and Training<br>
* http://www.olat.org
* <p>
* Licensed under the Apache License, Version 2.0 (the "License"); <br>
* you may not use this file except in compliance with the License.<br>
* You may obtain a copy of the License at
* <p>
* http://www.apache.org/licenses/LICENSE-2.0
* <p>
* Unless required by applicable law or agreed to in writing,<br>
* software distributed under the License is distributed on an "AS IS" BASIS, <br>
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. <br>
* See the License for the specific language governing permissions and <br>
* limitations under the License.
* <p>
* Copyright (c) since 2004 at Multimedia- & E-Learning Services (MELS),<br>
* University of Zurich, Switzerland.
* <hr>
* <a href="http://www.openolat.org">
* OpenOLAT - Online Learning and Training</a><br>
* This file has been modified by the OpenOLAT community. Changes are licensed
* under the Apache 2.0 license as the original file.  
* <p>
*/ 

package org.olat.core.commons.modules.bc.meta;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.poi.util.IOUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.olat.basesecurity.BaseSecurityManager;
import org.olat.core.commons.services.thumbnail.CannotGenerateThumbnailException;
import org.olat.core.commons.services.thumbnail.FinalSize;
import org.olat.core.commons.services.thumbnail.ThumbnailService;
import org.olat.core.gui.util.CSSHelper;
import org.olat.core.id.Identity;
import org.olat.core.logging.OLog;
import org.olat.core.logging.Tracing;
import org.olat.core.util.FileUtils;
import org.olat.core.util.StringHelper;
import org.olat.core.util.filter.FilterFactory;
import org.olat.core.util.vfs.LocalFileImpl;
import org.olat.core.util.vfs.OlatRelPathImpl;
import org.olat.core.util.vfs.VFSContainer;
import org.olat.core.util.vfs.VFSLeaf;
import org.olat.core.util.xml.XMLParser;
import org.olat.user.UserManager;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Initial Date:  08.07.2003
 *
 * @author Mike Stock<br>
 * 
 * Comment:
 * Meta files are in a shadow filesystem with the same directory structure as
 * their original files. Meta info for directories is stored in a file called
 * ".xml" residing in the respective directory. Meta info for files is stored
 * in a file with ".xml" appended to its filename.
 * 
 */
public class MetaInfoFileImpl extends DefaultHandler implements MetaInfo {
	private static OLog log = Tracing.createLoggerFor(MetaInfoFileImpl.class);
  private static SAXParser saxParser;
  static {
  	try {
  		saxParser = SAXParserFactory.newInstance().newSAXParser();
  	} catch(Exception ex) {
  		log.error("", ex);
  	}
  }
	
	// meta data
	private String uuid;
	private Long authorIdentKey = null;
	private Long lockedByIdentKey = null;
	private String comment = "";
	private String title, publisher, creator, source, city, pages, language, url, pubMonth, pubYear;
	private Date lockedDate;
	private int downloadCount;
	private boolean locked;
	
	// internal
	private File originFile = null;
	private File metaFile = null;
	
	private boolean cannotGenerateThumbnail = false;
	private List<Thumbnail> thumbnails = new ArrayList<Thumbnail>();
	private ThumbnailService thumbnailService;
	

	// make it a factory
	private MetaInfoFileImpl() { 
		//
	}

	protected MetaInfoFileImpl(File metaFile) { 
		this.metaFile = metaFile;
		parseSAX(metaFile);
	}
	
	protected MetaInfoFileImpl(String canonicalMetaPath, File metaFile, File originFile) { 
		this.metaFile = metaFile;
		this.originFile = originFile;
		// set
		if (!parseSAX(metaFile)) {
			String metaDirPath = canonicalMetaPath.substring(0, canonicalMetaPath.lastIndexOf('/'));
			new File(metaDirPath).mkdirs();
			if(uuid == null) {
				generateUUID();
			}
			write();
		}
	}

	public void setThumbnailService(ThumbnailService thumbnailService) {
		this.thumbnailService = thumbnailService;
	}
	
	/**
	 * Rename the given meta info file
	 * 
	 * @param meta
	 * @param newName
	 */
	public void rename(String newName) {
		// rename meta info file name
		if (isDirectory()) { // rename the directory, which is the parent of the actual ".xml" file
			File metaFileDirectory = metaFile.getParentFile();
			metaFileDirectory.renameTo(new File(metaFileDirectory.getParentFile(), newName));
		} else { // rename the file
			metaFile.renameTo(new File(metaFile.getParentFile(), newName + ".xml"));
		}
	}
	
	/**
	 * Move/Copy the given meta info to the target directory.
	 * @param targetDir
	 * @param move
	 */
	public void moveCopyToDir(OlatRelPathImpl target, boolean move) {
		File fSource = metaFile;
		File fTarget = new File(MetaInfoFactory.getCanonicalMetaPath(target));
		if (isDirectory()) { // move/copy whole meta directory
			fSource = fSource.getParentFile();
			fTarget = fTarget.getParentFile();
		} else if (target instanceof VFSContainer) {
			//getCanonicalMetaPath give the path to the xml file where the metadatas are saved
			if(fTarget.getName().equals(".xml")) {
				fTarget = fTarget.getParentFile();
			}
		}
		
		if (move) FileUtils.moveFileToDir(fSource, fTarget);
		else {
			//copy
			 Map<String,String> pathToUuid = new HashMap<String,String>();
			File mTarget = new File(fTarget, fSource.getName());
			collectUUIDRec(mTarget, pathToUuid);
			
			if(FileUtils.copyFileToDir(fSource, fTarget, "copy metadata")) {
				File endTarget = new File(fTarget, fSource.getName());
				generateUUIDRec(endTarget, pathToUuid);
			}
		}
	}
	
	private void collectUUIDRec(File mTarget, Map<String,String> pathToUuid) {
		try {
			if(mTarget.exists()) {
				if(mTarget.isDirectory()) {
					//TODO
				} else {
					MetaInfoFileImpl copyMeta = new MetaInfoFileImpl();
					copyMeta.metaFile = mTarget;
					if (copyMeta.parseSAX(mTarget)) {
						pathToUuid.put(mTarget.getCanonicalPath(), copyMeta.getUUID());
					}
				}
			}
		} catch (IOException e) {
			log.error("cannot collect current UUID before copy", e);
		}
	}

	private void generateUUIDRec(File endTarget, Map<String,String> pathToUuid) {
		if(!endTarget.exists()) {
			return;
		}

		try {
			if(endTarget.isDirectory()) {
				for(File subEndTarget:endTarget.listFiles(new XmlFilter())) {
					generateUUIDRec(subEndTarget, pathToUuid);
				}
			} else {
				MetaInfoFileImpl copyMeta = new MetaInfoFileImpl();
				copyMeta.metaFile = endTarget;
				if (copyMeta.parseSAX(endTarget)) {
					String tempUuid = pathToUuid.get(endTarget.getCanonicalPath());
					if(StringHelper.containsNonWhitespace(tempUuid)) {
						copyMeta.uuid =tempUuid;
					} else {
						copyMeta.generateUUID();
					}
					copyMeta.write();
				}
			}
		} catch (IOException e) {
			log.error("Cannot generate a new uuid on copy", e);
		}
	}
	
	/**
	 * Delete all associated meta info including sub files/directories
	 * @param meta
	 */
	public void deleteAll() {
		if (isDirectory()) { // delete whole meta directory (where the ".xml" resides within)
			FileUtils.deleteDirsAndFiles(metaFile.getParentFile(), true, true);
		} else { // delete this single meta file
			delete();
		}
	}
	
	/**
	 * Copy values from froMeta into this object except name.
	 * @param fromMeta
	 */
	public void copyValues(MetaInfo fromMeta) {
		this.setAuthor(fromMeta.getAuthor());
		this.setComment(fromMeta.getComment());
		this.setCity(fromMeta.getCity());
		this.setCreator(fromMeta.getCreator());
		this.setLanguage(fromMeta.getLanguage());
		this.setPages(fromMeta.getPages());
		this.setPublicationDate(fromMeta.getPublicationDate()[1], fromMeta.getPublicationDate()[0]);
		this.setPublisher(fromMeta.getPublisher());
		this.setSource(fromMeta.getSource());
		this.setTitle(fromMeta.getTitle());
		this.setUrl(fromMeta.getUrl());
	}

	public boolean isLocked() {
		return locked;
	}
	
	public void setLocked(boolean locked) {
		this.locked = locked;
		if(!locked) {
			lockedByIdentKey = null;
			lockedDate = null;
		}
	}

	public Identity getLockedByIdentity() {
		if(lockedByIdentKey != null) {
			Identity identity = BaseSecurityManager.getInstance().loadIdentityByKey(lockedByIdentKey);
			return identity;
		}
		return null;
	}

	public Long getLockedBy() {
		return lockedByIdentKey;
	}

	public void setLockedBy(Long lockedBy) {
		this.lockedByIdentKey = lockedBy;
	}

	public Date getLockedDate() {
		return lockedDate;
	}

	public void setLockedDate(Date lockedDate) {
		this.lockedDate = lockedDate;
	}

	/**
	 * Writes the meta data to file. If no changes have been made,
	 * does not write anything.
	 * @return True upon success.
	 */
	public boolean write() {
		BufferedOutputStream bos = null;
		if (metaFile == null) return false;
		try {
			bos = new BufferedOutputStream(new FileOutputStream(metaFile));
			OutputStreamWriter sw = new OutputStreamWriter(bos, Charset.forName("UTF-8"));
			sw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			sw.write("<meta");
			if(StringHelper.containsNonWhitespace(uuid)) {
				sw.write(" uuid=\"" + uuid + "\"");
			}
			sw.write(">");		
			sw.write("<author><![CDATA[" + (authorIdentKey == null ? "" : authorIdentKey.toString()) + "]]></author>");		
			sw.write("<lock locked=\"" + locked + "\"" + (lockedDate == null ? "" : " date=\"" + lockedDate.getTime() + "\"")	+ "><![CDATA[" + (lockedByIdentKey == null ? "" : lockedByIdentKey) + "]]></lock>");
			sw.write("<comment><![CDATA[" + filterForCData(comment) + "]]></comment>");
			sw.write("<title><![CDATA[" + filterForCData(title) + "]]></title>");
			sw.write("<publisher><![CDATA[" + filterForCData(publisher) + "]]></publisher>");
			sw.write("<creator><![CDATA[" + filterForCData(creator) + "]]></creator>");
			sw.write("<source><![CDATA[" + filterForCData(source) + "]]></source>");
			sw.write("<city><![CDATA[" + filterForCData(city) + "]]></city>");
			sw.write("<pages><![CDATA[" + filterForCData(pages) + "]]></pages>");
			sw.write("<language><![CDATA[" + filterForCData(language) + "]]></language>");
			sw.write("<url><![CDATA[" + filterForCData(url) + "]]></url>");
			sw.write("<publicationDate><month><![CDATA[" + (pubMonth != null ? pubMonth.trim() : "") + "]]></month><year><![CDATA[" + (pubYear != null ? pubYear.trim() : "") + "]]></year></publicationDate>");
			sw.write("<downloadCount><![CDATA[" + downloadCount + "]]></downloadCount>");
			sw.write("<thumbnails cannotGenerateThumbnail=\"" + cannotGenerateThumbnail + "\">");
			for(Thumbnail thumbnail:thumbnails) {
				sw.write("<thumbnail maxHeight=\"");
				sw.write(Integer.toString(thumbnail.getMaxHeight()));
				sw.write("\" maxWidth=\"");
				sw.write(Integer.toString(thumbnail.getMaxWidth()));
				sw.write("\" finalHeight=\"");
				sw.write(Integer.toString(thumbnail.getFinalHeight()));
				sw.write("\" finalWidth=\"");
				sw.write(Integer.toString(thumbnail.getFinalWidth()));
				sw.write("\">");
				sw.write("<![CDATA[" + thumbnail.getThumbnailFile().getName() + "]]>");
				sw.write("</thumbnail>");
			}
			sw.write("</thumbnails>");
			sw.write("</meta>");
			sw.close();
		} catch (Exception e) { 
			return false; 
		} finally {
			if (bos != null) {		
				try {
					bos.close();
				} catch (IOException e) {
					log.warn("Can not close stream, "+ e.getMessage());
				}
			}
		}
		return true;
	}
	
	private String filterForCData(String original) {
		if(StringHelper.containsNonWhitespace(original)) {
			return FilterFactory.getXMLValidCharacterFilter().filter(original);
		}
		return "";
	}
	
	/**
	 * Delete this meta info
	 * 
	 * @return True upon success.
	 */
	public boolean delete() {
		if (metaFile == null) return false;
		for(Thumbnail thumbnail:thumbnails) {
			File file = thumbnail.getThumbnailFile();
			if(file != null && file.exists()) {
				file.delete();
			}
		}
		return metaFile.delete();
	}
	
	/**
	 * The parser is synchronized. Normally for such small files, this is
	 * the quicker way. Creation of a SAXParser is really time consuming.
	 * An other possibilty would be to use a pool of parser.
	 * @param fMeta
	 * @return
	 */
	private boolean parseSAX(File fMeta) {
		if (fMeta == null || !fMeta.exists() || fMeta.isDirectory()) return false;
		
		InputStream in = null;
	  try {
		//the performance gain of the SAX Parser over the DOM Parser allow
		//this to be synchronized (factory 5 to 10 quicker)
	  	synchronized(saxParser) {
	  		in = new FileInputStream(fMeta);
	  		saxParser.parse(in, this);
	  		if(uuid == null) {
		  		generateUUID();
		  		write();
		  	}
	  	}
	  } catch (SAXParseException ex) {
	  	if(!parseSAXFiltered(fMeta)) {
	  		//OLAT-5383,OLAT-5468: lowered error to warn to reduce error noise
	  		log.warn("SAX Parser error while parsing " + fMeta, ex);
	  	}
	  } catch(Exception ex) {
	  	log.error("Error while parsing " + fMeta, ex);
	  } finally {
	  	IOUtils.closeQuietly(in);
	  }
		return true;
	}
	
	/**
	 * Try to rescue xml files with invalid characters
	 * @param fMeta
	 * @return true if rescue is successful
	 */
	private boolean parseSAXFiltered(File fMeta) {
		String original = FileUtils.load(fMeta, "UTF-8");
		if(original == null) return false;
		
		String filtered = FilterFactory.getXMLValidCharacterFilter().filter(original);
		if(original != null && !original.equals(filtered)) {
			try {
				synchronized(saxParser) {
					InputSource in = new InputSource(new StringReader(filtered));
					saxParser.parse(in, this);
				}
				write();//update with the new filtered write method
				return true;
			} catch (Exception e) {
				//only a fallback, fail silently
			}
		}
		return false;
	}
	
	/**
	 * Parse XML from file with SAX and fill-in MetaInfo attributes.
	 * @param fMeta
	 */
	@Deprecated
	public boolean parseXMLdom(File fMeta) {
		if (fMeta == null || !fMeta.exists()) return false;
		InputStream is;
		try {	
			is = new BufferedInputStream(new FileInputStream(fMeta)); 
		} catch (FileNotFoundException e) {	
			return false;	
		}
		
		try {
			XMLParser xmlp = new XMLParser();
			Document doc = xmlp.parse(is, false);
			if (doc == null) return false;
			
			// extract data from XML
			Element root = doc.getRootElement();
			Element n;
			n = root.element("author");
			if (n == null) {
				authorIdentKey = null;
			} else {
				if (n.getText().length() == 0 ) {
					authorIdentKey = null;
				} else {
					try {
						authorIdentKey = Long.valueOf(n.getText());
					} catch (NumberFormatException nEx) {
						authorIdentKey = null;
					}
				}
			}
			n = root.element("comment");
			comment = (n != null) ? n.getText() : "";
			Element lockEl = root.element("lock");
			if(lockEl != null) {
				locked = "true".equals(lockEl.attribute("locked").getValue());
				try {
					lockedByIdentKey = new Long(n.getText());
				} catch (NumberFormatException nEx) {
					lockedByIdentKey = null;
				}
			}
			n = root.element("title");
			title = (n != null) ? n.getText() : "";
			n = root.element("publisher");
			publisher = (n != null) ? n.getText() : "";
			n = root.element("source");
			source = (n != null) ? n.getText() : "";
			n = root.element("creator");
			creator = (n != null) ? n.getText() : "";
			n = root.element("city");
			city = (n != null) ? n.getText() : "";
			n = root.element("pages");
			pages = (n != null) ? n.getText() : "";
			n = root.element("language");
			language = (n != null) ? n.getText() : "";
			n = root.element("url");
			url = (n != null) ? n.getText() : "";
			n = root.element("downloadCount");
			downloadCount = (n != null) ? Integer.valueOf(n.getText()) : 0;
			n = root.element("publicationDate");
			if (n != null) {
				Element m = n.element("month");
				pubMonth = (m != null) ? m.getText() : "";
				 m = n.element("year");
				pubYear = (m != null) ? m.getText() : "";
			}
			return true;
		} catch (Exception ex) {
			log.warn("Corrupted metadata file: " + fMeta);
			return false;
		}
	}
	
	/* ------------------------- Getters ------------------------------ */
	
	/**
	 * @return name of the initial author
	 */
	public String getAuthor() { 
		if (authorIdentKey == null) {
			return "-";
		} else {
			try {
				Identity identity = BaseSecurityManager.getInstance().loadIdentityByKey(authorIdentKey);
				if (identity == null) {
					log.warn("Found no idenitiy with key='" + authorIdentKey + "'");
					return "-";
				}
				return identity.getName();
			} catch (Exception e) {
				return "-";
			}
		}	
	}
	
	@Override
	public String getUUID() {
		return uuid;
	}
	
	public void generateUUID() {
		uuid = UUID.randomUUID().toString().replace("-", "");
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getAuthorIdentity()
	 */
	public Identity getAuthorIdentity() {
		if (this.authorIdentKey == null) {
			return null;
		} else {
			return BaseSecurityManager.getInstance().loadIdentityByKey(this.authorIdentKey);
		}
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getHTMLFormattedAuthor()
	 */
	public String getHTMLFormattedAuthor() {
		if (authorIdentKey == null) {
			return "-";
		} else {
			String fullName = UserManager.getInstance().getUserDisplayName(authorIdentKey);
			if (fullName == null) {
				log.warn("Found no identity with key='" + authorIdentKey + "'");
				return "-";
			}
			return fullName; 
		}	
	}
	
	/**
	 * @return comment
	 */
	public String getComment() { return comment; }

	public String getName() { return originFile.getName(); }
	/**
	 * @return True if this is a directory
	 */
	public boolean isDirectory() { return originFile.isDirectory(); }

	/**
	 * @return Last modified timestamp
	 */
	public long getLastModified() { return originFile.lastModified(); }

	/**
	 * @return size of file
	 */
	public long getSize() {	return originFile.length(); }
	
	/**
	 * @return formatted representation of size of file
	 */
	public String getFormattedSize() { return StringHelper.formatMemory(getSize()); }

	/* ------------------------- Setters ------------------------------ */
	
	/**
	 * @param string
	 */
	public void setAuthor(String username) { 
		Identity identity = BaseSecurityManager.getInstance().findIdentityByName(username);
		if (identity == null) {
			log.warn("Found no idenitiy with username='" + username + "'");
			authorIdentKey = null;
			return;
		}
		authorIdentKey = identity.getKey(); 
	}

	@Override
	public void setAuthor(Identity identity) {
		if (identity == null) {
			log.warn("Found no idenity");
			authorIdentKey = null;
			return;
		}
		authorIdentKey = identity.getKey(); 
	}

	/**
	 * @param string
	 */
	public void setComment(String string) { comment = string; }

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Name [" + getName());
		sb.append("] Author [" + getAuthor());
		sb.append("] Comment [" + getComment());
		sb.append("] IsDirectory [" + isDirectory());
		sb.append("] Size [" + getFormattedSize());
		sb.append("] LastModified [" + new Date(getLastModified()) + "]");
		return sb.toString();
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getCity()
	 */
	public String getCity() {
		return city;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getLanguage()
	 */
	public String getLanguage() {
		return language;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getPages()
	 */
	public String getPages() {
		return pages;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getPublishDate()
	 */
	public String[] getPublicationDate() {
		return new String[] { pubYear, pubMonth };
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getPublisher()
	 */
	public String getPublisher() {
		return publisher;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getCreator()
	 */
	public String getCreator() {
		return creator;
	}
	
	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getSource()
	 */
	public String getSource() {
		return source;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getTitle()
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getUrl()
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#setCity(java.lang.String)
	 */
	public void setCity(String city) {
		this.city = city;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#setLanguage(java.lang.String)
	 */
	public void setLanguage(String language) {
		this.language = language;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#setPages(java.lang.String)
	 */
	public void setPages(String pages) {
		this.pages = pages;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#setPublishDate(java.lang.String)
	 */
	public void setPublicationDate(String month, String year) {
		this.pubMonth = month;
		this.pubYear = year;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#setPublisher(java.lang.String)
	 */
	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#setWriter(java.lang.String)
	 */
	public void setWriter(String writer) {
		this.creator = writer;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#setWriter(java.lang.String)
	 */
	public void setCreator(String creator) {
		this.creator = creator;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#setSource(java.lang.String)
	 */
	public void setSource(String source) {
		this.source = source;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#setTitle(java.lang.String)
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#setUrl(java.lang.String)
	 */
	public void setUrl(String url) {
		this.url = url;
	}
	
	public boolean isThumbnailAvailable() {
		if(isDirectory()) return false;
		if(originFile.isHidden()) return false;
		if(cannotGenerateThumbnail) return false;
		
		VFSLeaf originLeaf = new LocalFileImpl(originFile);
		if (thumbnailService != null) return  thumbnailService.isThumbnailPossible(originLeaf);
		return false;
	}
	
	public VFSLeaf getThumbnail(int maxWidth, int maxHeight) {
		if(isDirectory()) return null;
		Thumbnail thumbnailInfo =  getThumbnailInfo(maxWidth, maxHeight);
		if(thumbnailInfo == null) {
			return null;
		}
		return new LocalFileImpl(thumbnailInfo.getThumbnailFile());
	}
	
	@Override
	public void clearThumbnails() {
		cannotGenerateThumbnail = false;
		for(Thumbnail thumbnail:thumbnails) {
			File thumbnailFile = thumbnail.getThumbnailFile();
			if(thumbnailFile != null && thumbnailFile.exists()) {
				thumbnailFile.delete();
			}
		}
		thumbnails.clear();
		write();
	}

	private Thumbnail getThumbnailInfo(int maxWidth, int maxHeight) {
		for(Thumbnail thumbnail:thumbnails) {
			if(maxHeight == thumbnail.getMaxHeight() && maxWidth == thumbnail.getMaxWidth()) {
				if(thumbnail.exists()) {
					return thumbnail;
				}
			}
		}

		//generate a file name
		File metaLoc = metaFile.getParentFile();
		String name = originFile.getName();
		String extension = FileUtils.getFileSuffix(name);
		String nameOnly = name.substring(0, name.length() - extension.length() - 1);
		String uuid = UUID.randomUUID().toString();
		String thumbnailExtension = preferedThumbnailType(extension);
		File thumbnailFile = new File(metaLoc, nameOnly + "_" + uuid + "_" + maxHeight + "x" + maxWidth + "." + thumbnailExtension);
		
		//generate thumbnail
		long start = 0l;
		if(log.isDebug()) start = System.currentTimeMillis();
		
		VFSLeaf thumbnailLeaf = new LocalFileImpl(thumbnailFile);
		VFSLeaf originLeaf = new LocalFileImpl(originFile);
		if(thumbnailService != null &&thumbnailService.isThumbnailPossible(thumbnailLeaf)) {
			try {
				if(thumbnails.isEmpty()) {
					//be paranoid
					cannotGenerateThumbnail = true;
					write();
				}
				log.info("Start thumbnail: " + thumbnailLeaf);
				
				FinalSize finalSize = thumbnailService.generateThumbnail(originLeaf, thumbnailLeaf, maxHeight, maxWidth);
				if(finalSize == null) {
					return null;
				} else {
					Thumbnail thumbnail = new Thumbnail();
					thumbnail.setMaxHeight(maxHeight);
					thumbnail.setMaxWidth(maxWidth);
					thumbnail.setFinalHeight(finalSize.getHeight());
					thumbnail.setFinalWidth(finalSize.getWidth());
					thumbnail.setThumbnailFile(thumbnailFile);
					thumbnails.add(thumbnail);
					cannotGenerateThumbnail = false;
					write();
					log.info("Create thumbnail: " + thumbnailLeaf);
					if(log.isDebug()) { 
						log.debug("Creation of thumbnail takes (ms): " + (System.currentTimeMillis() - start));
					}
					return thumbnail;
				}
			} catch (CannotGenerateThumbnailException e) {
				//don't try every time to create the thumbnail.
				cannotGenerateThumbnail = true;
				write();
				return null;
			}
		}
		return null;
	}
	
	private String preferedThumbnailType(String extension) {
		if(extension.equalsIgnoreCase("png") || extension.equalsIgnoreCase("gif")) {
			return extension;
		}
		if(extension.equalsIgnoreCase("pdf")) {
			return "png";
		}
		return "jpg";
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#increaseDownloadCount()
	 */
	public void increaseDownloadCount() {
		this.downloadCount++;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getDownloadCount()
	 */
	public int getDownloadCount() {
		return downloadCount;
	}

	public void setAuthorIdentKey(Long authorIdentKey) {
		this.authorIdentKey = authorIdentKey;
	}
	
	private StringBuilder current;
	
	////////////////////////////////////
	// SAX Handler for max. performance
	////////////////////////////////////
	
	@Override
	public final void startElement(String uri, String localName, String qName, Attributes attributes) {
		if("meta".equals(qName)) {
			uuid = attributes.getValue("uuid");
		} else if ("lock".equals(qName)) {
			locked ="true".equals(attributes.getValue("locked"));
			String date = attributes.getValue("date");
			if (date != null && date.length() > 0) {
				lockedDate = new Date(Long.parseLong(date));
			}
		} else if ("thumbnails".equals(qName)) {
			String valueStr = attributes.getValue("cannotGenerateThumbnail");
			if(StringHelper.containsNonWhitespace(valueStr)) {
				cannotGenerateThumbnail = new Boolean(valueStr);
			}
		}	else if ("thumbnail".equals(qName)) {
			Thumbnail thumbnail = new Thumbnail();
			thumbnail.setMaxHeight(Integer.parseInt(attributes.getValue("maxHeight")));
			thumbnail.setMaxWidth(Integer.parseInt(attributes.getValue("maxWidth")));
			thumbnail.setFinalHeight(Integer.parseInt(attributes.getValue("finalHeight")));
			thumbnail.setFinalWidth(Integer.parseInt(attributes.getValue("finalWidth")));
			thumbnails.add(thumbnail);
		}
	}
	
	@Override
	public final void characters(char[] ch, int start, int length) {
		if(length == 0) return;
		if(current == null) {
			current = new StringBuilder();
		}
		current.append(ch, start, length);
	}

	@Override
	public final void endElement(String uri, String localName, String qName) {
		if(current == null) return;
		
		if("comment".equals(qName)) {
			comment = current.toString();
		} else if ("author".equals(qName)) {
			try {
				authorIdentKey = Long.valueOf(current.toString());
			} catch (NumberFormatException nEx) {
				//nothing to say
			}
		} else if ("lock".equals(qName)) {
			try {
				lockedByIdentKey = new Long(current.toString());
			} catch (NumberFormatException nEx) {
				//nothing to say
			}
		} else if ("title".equals(qName)) {
			title = current.toString();
		} else if ("publisher".equals(qName)) {
			publisher = current.toString();
		} else if ("source".equals(qName)) {
			source = current.toString();
		} else if ("city".equals(qName)) {
			city = current.toString();
		} else if ("pages".equals(qName)) {
			pages = current.toString();
		} else if ("language".equals(qName)) {
			language = current.toString();
		} else if ("downloadCount".equals(qName)) {
			try {
				downloadCount = Integer.valueOf(current.toString());
			} catch (NumberFormatException nEx) {
				//nothing to say
			}
		} else if ("month".equals(qName)) {
			pubMonth = current.toString();
		} else if ("year".equals(qName)) {
			pubYear = current.toString();
		} else if (qName.equals("creator")) {
			this.creator = current.toString();
		} else if (qName.equals("url")) {
			this.url = current.toString();
		} else if (qName.equals("thumbnail")) {
			String finalName = current.toString();
			File thumbnailFile = new File(metaFile.getParentFile(), finalName);
			thumbnails.get(thumbnails.size() - 1).setThumbnailFile(thumbnailFile);
		}
		current = null;
	}

	/**
	 * @see org.olat.core.commons.modules.bc.meta.MetaInfo#getIconCssClass()
	 */
	@Override
	public String getIconCssClass() {
		String cssClass;
		if (isDirectory()) {
			cssClass =  CSSHelper.CSS_CLASS_FILETYPE_FOLDER;
		} else {
			cssClass = CSSHelper.createFiletypeIconCssClassFor(getName());
		}
		return cssClass;
	}
	
	public static class XmlFilter implements FileFilter {
		@Override
		public boolean accept(File file) {
			return file.getName().endsWith(".xml");
		}
	}
	
	public static class Thumbnail {
		private int maxWidth;
		private int maxHeight;
		private int finalWidth;
		private int finalHeight;
		private File thumbnailFile;
		
		public int getMaxWidth() {
			return maxWidth;
		}
		
		public void setMaxWidth(int maxWidth) {
			this.maxWidth = maxWidth;
		}
		
		public int getMaxHeight() {
			return maxHeight;
		}
		
		public void setMaxHeight(int maxHeight) {
			this.maxHeight = maxHeight;
		}
		
		public int getFinalWidth() {
			return finalWidth;
		}
		
		public void setFinalWidth(int finalWidth) {
			this.finalWidth = finalWidth;
		}
		
		public int getFinalHeight() {
			return finalHeight;
		}
		
		public void setFinalHeight(int finalHeight) {
			this.finalHeight = finalHeight;
		}
		
		public File getThumbnailFile() {
			return thumbnailFile;
		}
		
		public void setThumbnailFile(File thumbnailFile) {
			this.thumbnailFile = thumbnailFile;
		}
		
		public boolean exists() {
			return thumbnailFile == null ? false : thumbnailFile.exists();
		}
	}
}