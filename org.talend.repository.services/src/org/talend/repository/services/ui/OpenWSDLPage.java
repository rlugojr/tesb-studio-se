// ============================================================================
//
// Copyright (C) 2006-2011 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.repository.services.ui;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.wsdl.Definition;
import javax.wsdl.Operation;
import javax.wsdl.PortType;
import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.talend.commons.exception.PersistenceException;
import org.talend.commons.ui.swt.formtools.LabelledFileField;
import org.talend.core.model.properties.ByteArray;
import org.talend.core.model.properties.PropertiesFactory;
import org.talend.core.model.properties.ReferenceFileItem;
import org.talend.core.model.repository.ERepositoryObjectType;
import org.talend.core.model.repository.RepositoryViewObject;
import org.talend.core.repository.model.ResourceModelUtils;
import org.talend.repository.ProjectManager;
import org.talend.repository.model.IRepositoryNode.ENodeType;
import org.talend.repository.model.IRepositoryNode.EProperties;
import org.talend.repository.model.RepositoryNode;
import org.talend.repository.services.model.services.ServiceItem;
import org.talend.repository.services.model.services.ServiceOperation;
import org.talend.repository.services.model.services.ServicePort;
import org.talend.repository.services.model.services.ServicesFactory;

/**
 * hwang class global comment. Detailled comment
 */
public class OpenWSDLPage extends WizardPage {

    private RepositoryNode repositoryNode;

    private LabelledFileField wsdlText;

    private String path;

    private Definition definition;

    protected OpenWSDLPage(RepositoryNode repositoryNode, String pageName) {
        super(pageName);
        this.repositoryNode = repositoryNode;
        this.setTitle("Edit WSDL");
        this.setMessage("choose a WSDL file");
    }

    public void createControl(Composite parent) {
        Composite parentArea = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.numColumns = 5;
        parentArea.setLayout(layout);

        String[] xmlExtensions = { "*.xml;*.xsd;*.wsdl", "*.*", "*" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        wsdlText = new LabelledFileField(parentArea, "WSDL", //$NON-NLS-1$
                xmlExtensions);
        ServiceItem item = (ServiceItem) repositoryNode.getObject().getProperty().getItem();
        if (item.getServiceConnection() != null) {
            wsdlText.setText(item.getServiceConnection().getWSDLPath());
        }
        path = wsdlText.getText();

        setPageComplete(false);
        addListener();
        setControl(parentArea);

    }

    private void addListener() {
        wsdlText.addModifyListener(new ModifyListener() {

            public void modifyText(ModifyEvent e) {
                path = wsdlText.getText();
                if (path.trim().length() > 0) {
                    setPageComplete(true);
                } else {
                    setPageComplete(false);
                }
            }

        });
    }

    public void finish() {
        ServiceItem item = (ServiceItem) repositoryNode.getObject().getProperty().getItem();
        if (path != null && !path.trim().equals("")) {
            item.setServiceConnection(ServicesFactory.eINSTANCE.createServiceConnection());
            item.getServiceConnection().setWSDLPath(path);
            File file = new File(path);
            StringBuffer buffer = new StringBuffer();
            try {
                BufferedReader read = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                String temp;
                while ((temp = read.readLine()) != null) {
                    buffer.append(temp);
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // copy file to item
            try {
                IProject currentProject = ResourceModelUtils.getProject(ProjectManager.getInstance().getCurrentProject());
                String foldPath = item.getState().getPath();
                String folder = "";
                if (!foldPath.equals("")) {
                    folder = "/" + foldPath;
                }
                IFile fileTemp = currentProject.getFolder("services" + folder).getFile(
                        repositoryNode.getObject().getProperty().getLabel() + "_"
                                + repositoryNode.getObject().getProperty().getVersion() + ".wsdl");
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buffer.toString().getBytes());
                if (!fileTemp.exists()) {
                    fileTemp.create(byteArrayInputStream, true, null);
                } else {
                    fileTemp.setContents(byteArrayInputStream, 0, null);
                }
            } catch (PersistenceException e1) {
                e1.printStackTrace();
            } catch (CoreException e) {
                e.printStackTrace();
            }
            //
            if (item.getReferenceResources().size() == 0) {
                ReferenceFileItem createReferenceFileItem = PropertiesFactory.eINSTANCE.createReferenceFileItem();
                ByteArray byteArray = PropertiesFactory.eINSTANCE.createByteArray();
                createReferenceFileItem.setContent(byteArray);
                createReferenceFileItem.setExtension("wsdl");
                item.getReferenceResources().add(createReferenceFileItem);
                createReferenceFileItem.getContent().setInnerContent(buffer.toString().getBytes());
            } else {
                ((ReferenceFileItem) item.getReferenceResources().get(0)).getContent().setInnerContent(
                        buffer.toString().getBytes());
            }
            //
            WSDLFactory wsdlFactory;
            try {
                wsdlFactory = WSDLFactory.newInstance();
                WSDLReader newWSDLReader = wsdlFactory.newWSDLReader();
                newWSDLReader.setExtensionRegistry(wsdlFactory.newPopulatedExtensionRegistry());
                newWSDLReader.setFeature(com.ibm.wsdl.Constants.FEATURE_VERBOSE, false);
                definition = newWSDLReader.readWSDL(path);
                Map portTypes = definition.getAllPortTypes();
                Iterator it = portTypes.keySet().iterator();
                repositoryNode.getChildren().clear();
                item.getServiceConnection().getServicePort().clear();
                while (it.hasNext()) {
                    QName key = (QName) it.next();
                    PortType portType = (PortType) portTypes.get(key);
                    ServicePort port = ServicesFactory.eINSTANCE.createServicePort();
                    port.setName(portType.getQName().getLocalPart());
                    List<Operation> list = portType.getOperations();
                    for (Operation operation : list) {
                        ServiceOperation serviceOperation = ServicesFactory.eINSTANCE.createServiceOperation();
                        RepositoryNode operationNode = new RepositoryNode(new RepositoryViewObject(item.getProperty()),
                                repositoryNode, ENodeType.REPOSITORY_ELEMENT);
                        operationNode.setProperties(EProperties.LABEL, repositoryNode.getObject().getLabel());
                        operationNode.setProperties(EProperties.CONTENT_TYPE, ERepositoryObjectType.SERVICESOPERATION);
                        serviceOperation.setOperationName(operation.getName());
                        if (operation.getDocumentationElement() != null) {
                            serviceOperation.setDocumentation(operation.getDocumentationElement().getTextContent());
                        }
                        serviceOperation.setLabel(operation.getName());
                        port.getServiceOperation().add(serviceOperation);
                    }
                    item.getServiceConnection().getServicePort().add(port);
                }
            } catch (WSDLException e) {
                e.printStackTrace();
            }
        }
    }

}
