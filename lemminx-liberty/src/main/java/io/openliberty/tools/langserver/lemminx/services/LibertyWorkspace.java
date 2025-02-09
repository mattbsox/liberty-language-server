/*******************************************************************************
* Copyright (c) 2020, 2022 IBM Corporation and others.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation - initial API and implementation
*******************************************************************************/
package io.openliberty.tools.langserver.lemminx.services;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import io.openliberty.tools.langserver.lemminx.models.feature.Feature;
import io.openliberty.tools.langserver.lemminx.models.settings.DevcMetadata;
import io.openliberty.tools.langserver.lemminx.util.LibertyUtils;

public class LibertyWorkspace {

    private static final Logger LOGGER = Logger.getLogger(LibertyWorkspace.class.getName());

    private String workspaceFolderURI;
    private String libertyVersion;
    private String libertyRuntime;
    private boolean isLibertyInstalled;
    private List<Feature> installedFeatureList;
    private Set<String> configFiles;

    // devc vars
    private String containerName;
    private boolean containerAlive;

    /**
     * Model of a Liberty Workspace. Each workspace indicates the
     * workspaceFolderURI, the Liberty version associated (may be cached), and if an
     * installed Liberty instance has been detected.
     * 
     * @param workspaceFolderURI
     */
    public LibertyWorkspace(String workspaceFolderURI) {
        this.workspaceFolderURI = workspaceFolderURI;
        this.libertyVersion = null;
        this.libertyRuntime = null;
        this.isLibertyInstalled = false;
        this.installedFeatureList = new ArrayList<Feature>();
        this.containerName = null;
        this.containerAlive = false;

        this.configFiles = new HashSet<String>();
        initConfigFileList();
    }

    public String getWorkspaceString() {
        return this.workspaceFolderURI;
    }

    public URI getWorkspaceURI() {
        return URI.create(this.workspaceFolderURI);
    }

    public File getDir() {
        return new File(URI.create(getWorkspaceString()).getPath());
    }

    public void setLibertyVersion(String libertyVersion) {
        this.libertyVersion = libertyVersion;
    }

    public String getLibertyVersion() {
        return this.libertyVersion;
    }

    public void setLibertyRuntime(String libertyRuntime) {
        this.libertyRuntime = libertyRuntime;
    }

    public String getLibertyRuntime() {
        return libertyRuntime;
    }

    public void setLibertyInstalled(boolean isLibertyInstalled) {
        this.isLibertyInstalled = isLibertyInstalled;
    }

    public boolean isLibertyInstalled() {
        return this.isLibertyInstalled;
    }

    public List<Feature> getInstalledFeatureList() {
        return this.installedFeatureList;
    }

    public void setInstalledFeatureList(List<Feature> installedFeatureList) {
        this.installedFeatureList = installedFeatureList;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public boolean isContainerAlive() {
        return this.containerAlive;
    }

    public void setContainerAlive(boolean containerAlive) {
        this.containerAlive = containerAlive;
    }

    /**
     * Return the path of the first *-liberty-devc-metadata.xml 
     * in the workspace with a running container
     * @return Path of *-liberty-devc-metadata.xml
     */
    public Path findDevcMetadata() {
        try {
            List<Path> metaDataList = Files
                    .find(Paths.get(getWorkspaceURI()), Integer.MAX_VALUE,
                            (filePath, fileAttributes) -> filePath.toString().endsWith("-liberty-devc-metadata.xml"))
                    .collect(Collectors.toList());
            for (Path metaDataFile : metaDataList) {
                DevcMetadata devcMetadata = unmarshalDevcMetadataFile(metaDataFile);
                if (devcMetadata.isContainerAlive()) {
                    setContainerName(devcMetadata.getContainerName());
                    setContainerAlive(true);
                    return metaDataFile;
                }
            }
            setContainerAlive(false);
            return null;
        } catch (IOException e) {
            // workspace URI does not exist
            LOGGER.warning("Workspace URI does not exist: " + e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to unmarshal/read the provided liberty-devc-metadata file.
     * @param devcMetadataFile
     * @return DevcMetadata object
     */
    public static DevcMetadata unmarshalDevcMetadataFile(Path devcMetadataFile) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(DevcMetadata.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            return (DevcMetadata)jaxbUnmarshaller.unmarshal(devcMetadataFile.toFile());
        } catch (JAXBException e) {
            // LOGGER.warning("Unable to unmarshal the devc metadata file: " + devcMetadataFile.toString());
            return null;
        }
    }

    private void initConfigFileList() {
        try {
            List<Path> serverXmlList = Files
                    .find(Paths.get(getWorkspaceURI()), Integer.MAX_VALUE,
                            (filePath, fileAttributes) -> LibertyUtils.isServerXMLFile(filePath.toString()))
                    .collect(Collectors.toList());
            for (Path serverXml : serverXmlList) {
                scanForConfigLocations(serverXml);
            }
        } catch (IOException e) {
            // workspace URI does not exist
            LOGGER.warning("Workspace URI does not exist: " + e.getMessage());
        }
    }

    // TODO: or use DOM
    private void scanForConfigLocations(Path filePath) {
        try {
            String workspacePath = this.getDir().getCanonicalPath();
            String content = new String(Files.readAllBytes(filePath));
            // [^<] = All characters, including whitespaces/newlines, not equivalent to '<'
            // [\"\'] = Accept either " or ' as string indicators
            // [\\s\\S]+? = Anything up to first closing tag
            String regex = "<include[^<]+location=[\"\'](.+)[\"\'][\\s\\S]+?/>";
            Matcher m = Pattern.compile(regex).matcher(content);
            while (m.find()) {
                // m.group(0) contains whole include element, m.group(1) contains only location value
                String locationFilePath = new File(filePath.getParent().toFile(), m.group(1)).getCanonicalPath();
                if (locationFilePath.startsWith(workspacePath)) {
                    // only recognize files that are in the same Liberty workspace 
                    // this guards against path traversal vulnerabilities
                    configFiles.add(locationFilePath);
                }
            }
        } catch (IOException e) {
            // specified config resources file does not exist
        } catch (Exception e) {
            LOGGER.warning("Exception received when scanning for config files: " + e.getMessage());
        }
    }

    public void addConfigFile(String fileString) {
        configFiles.add(fileString);
    }

    public boolean hasConfigFile(String fileString) {
        try {
            fileString = fileString.startsWith("file:") ? 
                    new File(URI.create(fileString)).getCanonicalPath() :
                    new File(fileString).getCanonicalPath();
            return this.configFiles.contains(fileString);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return workspaceFolderURI;
    }

}
