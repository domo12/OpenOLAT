/**
 * <a href="http://www.openolat.org">
 * OpenOLAT - Online Learning and Training</a><br>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); <br>
 * you may not use this file except in compliance with the License.<br>
 * You may obtain a copy of the License at the
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache homepage</a>
 * <p>
 * Unless required by applicable law or agreed to in writing,<br>
 * software distributed under the License is distributed on an "AS IS" BASIS, <br>
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. <br>
 * See the License for the specific language governing permissions and <br>
 * limitations under the License.
 * <p>
 * Initial code contributed and copyrighted by<br>
 * frentix GmbH, http://www.frentix.com
 * <p>
 */
package org.olat.modules.dcompensation.ui;

import java.util.Date;

import org.olat.core.gui.UserRequest;
import org.olat.core.gui.components.form.flexible.FormItem;
import org.olat.core.gui.components.form.flexible.FormItemContainer;
import org.olat.core.gui.components.form.flexible.elements.DateChooser;
import org.olat.core.gui.components.form.flexible.elements.FormLink;
import org.olat.core.gui.components.form.flexible.elements.SingleSelection;
import org.olat.core.gui.components.form.flexible.elements.TextElement;
import org.olat.core.gui.components.form.flexible.impl.FormBasicController;
import org.olat.core.gui.components.form.flexible.impl.FormEvent;
import org.olat.core.gui.components.form.flexible.impl.FormLayoutContainer;
import org.olat.core.gui.components.link.Link;
import org.olat.core.gui.components.util.KeyValues;
import org.olat.core.gui.control.Controller;
import org.olat.core.gui.control.Event;
import org.olat.core.gui.control.WindowControl;
import org.olat.core.gui.control.generic.closablewrapper.CloseableModalController;
import org.olat.core.id.Identity;
import org.olat.core.util.StringHelper;
import org.olat.core.util.Util;
import org.olat.core.util.tree.TreeVisitor;
import org.olat.core.util.tree.Visitor;
import org.olat.course.CourseFactory;
import org.olat.course.CourseModule;
import org.olat.course.ICourse;
import org.olat.course.nodes.CourseNode;
import org.olat.course.nodes.IQTESTCourseNode;
import org.olat.modules.dcompensation.DisadvantageCompensation;
import org.olat.modules.dcompensation.DisadvantageCompensationService;
import org.olat.modules.dcompensation.DisadvantageCompensationAuditLog.Action;
import org.olat.repository.RepositoryEntry;
import org.olat.repository.controllers.ReferencableEntriesSearchController;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * Initial date: 22 sept. 2020<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
public class UserDisadvantageCompensationEditController extends FormBasicController {

	private DateChooser approvalEl;
	private TextElement extraTimeEl;
	private TextElement approvedByEl;
	private FormLink selectEntryButton;
	private SingleSelection elementEl;
	private FormLayoutContainer selectRepositoryEntryLayout;

	private RepositoryEntry entry;
	private Identity disadvantagedIdentity;
	private DisadvantageCompensation compensation;
	
	@Autowired
	private DisadvantageCompensationService disadvantageCompensationService;
	
	private CloseableModalController cmc;
	private ReferencableEntriesSearchController searchFormCtrl;

	public UserDisadvantageCompensationEditController(UserRequest ureq, WindowControl wControl, DisadvantageCompensation compensation) {
		super(ureq, wControl);
		this.compensation = compensation;
		this.entry = compensation.getEntry();
		initForm(ureq);
	}
	
	public UserDisadvantageCompensationEditController(UserRequest ureq, WindowControl wControl, Identity disadvantagedIdentity) {
		super(ureq, wControl);
		this.disadvantagedIdentity = disadvantagedIdentity;
		initForm(ureq);
	}

	@Override
	protected void initForm(FormItemContainer formLayout, Controller listener, UserRequest ureq) {
		
		String approvedBy = compensation == null ? null : compensation.getApprovedBy();
		approvedByEl = uifactory.addTextElement("edit.approved.by", 255, approvedBy, formLayout);
		approvedByEl.setMandatory(true);
		
		Date approval = compensation == null ? null : compensation.getApproval();
		approvalEl = uifactory.addDateChooser("edit.approval.date", approval, formLayout);
		approvalEl.setMandatory(true);
		
		String extraTime = (compensation == null || compensation.getExtraTime() == null)
				? null : Integer.toString(compensation.getExtraTime().intValue() / 60);
		extraTimeEl = uifactory.addTextElement("edit.extra.time", 5, extraTime, formLayout);
		extraTimeEl.setDisplaySize(5);
		extraTimeEl.setDomReplacementWrapperRequired(false);
		extraTimeEl.setMandatory(true);

		String editPage = Util.getPackageVelocityRoot(getClass()) + "/select_repository_entry.html";
		selectRepositoryEntryLayout = FormLayoutContainer.createCustomFormLayout("selectFormLayout", getTranslator(), editPage);
		selectRepositoryEntryLayout.setLabel("edit.entry", null);
		selectRepositoryEntryLayout.setMandatory(true);
		formLayout.add(selectRepositoryEntryLayout);
		if(compensation != null && compensation.getEntry() != null) {
			String displayName = StringHelper.escapeHtml(compensation.getEntry().getDisplayname());
			selectRepositoryEntryLayout.contextPut("entryName", displayName);
		}
		selectEntryButton = uifactory.addFormLink("select.entry", selectRepositoryEntryLayout, Link.BUTTON);
		selectEntryButton.getComponent().setSuppressDirtyFormWarning(true);
		selectEntryButton.setVisible(compensation == null || compensation.getEntry() == null);
		
		elementEl = uifactory.addDropdownSingleselect("select.entry.element", formLayout, new String[0], new String[0]);
		if(compensation != null && compensation.getEntry() != null) {
			updateElementSelection(compensation.getEntry());
		}
		
		FormLayoutContainer buttonsCont = FormLayoutContainer.createButtonLayout("buttons", getTranslator());
		formLayout.add(buttonsCont);
		uifactory.addFormCancelButton("cancel", buttonsCont, ureq, getWindowControl());
		uifactory.addFormSubmitButton("save", buttonsCont);
	}

	@Override
	protected void doDispose() {
		//
	}

	@Override
	protected boolean validateFormLogic(UserRequest ureq) {
		boolean allOk = super.validateFormLogic(ureq);
		
		elementEl.clearError();
		if(!elementEl.isOneSelected()) {
			elementEl.setErrorKey("form.legende.mandatory", null);
			allOk &= false;
		}
		
		approvedByEl.clearError();
		if(!StringHelper.containsNonWhitespace(approvedByEl.getValue())) {
			approvedByEl.setErrorKey("form.legende.mandatory", null);
			allOk &= false;
		}
		
		approvalEl.clearError();
		if(approvalEl.getDate() == null) {
			approvalEl.setErrorKey("form.legende.mandatory", null);
			allOk &= false;
		}
		
		extraTimeEl.clearError();
		if(StringHelper.containsNonWhitespace(extraTimeEl.getValue())) {
			if(StringHelper.isLong(extraTimeEl.getValue())) {
				try {
					Integer.parseInt(extraTimeEl.getValue());
				} catch (NumberFormatException e) {
					extraTimeEl.setErrorKey("form.error.nointeger", null);
					allOk &= false;
				}
			} else {
				extraTimeEl.setErrorKey("form.error.nointeger", null);
				allOk &= false;
			}
		} else {
			extraTimeEl.setErrorKey("form.legende.mandatory", null);
			allOk &= false;
		}
		
		selectRepositoryEntryLayout.clearError();
		if(entry == null) {
			selectRepositoryEntryLayout.setErrorKey("form.legende.mandatory", null);
			allOk &= false;
		}
		
		return allOk;
	}

	@Override
	protected void event(UserRequest ureq, Controller source, Event event) {
		if(searchFormCtrl == source) {
			if(event == ReferencableEntriesSearchController.EVENT_REPOSITORY_ENTRY_SELECTED) {
				doSelectForm(searchFormCtrl.getSelectedEntry());
			}
			cmc.deactivate();
			cleanUp();
		} else if(cmc == source) {
			cleanUp();
		}
		super.event(ureq, source, event);
	}

	private void cleanUp() {
		removeAsListenerAndDispose(searchFormCtrl);
		removeAsListenerAndDispose(cmc);
		searchFormCtrl = null;
		cmc = null;
	}
	
	@Override
	protected void formOK(UserRequest ureq) {
		Date approval = approvalEl.getDate();
		String approvedBy = approvedByEl.getValue();
		int extraTime = Integer.parseInt(extraTimeEl.getValue()) * 60;
		String subIdent = elementEl.getSelectedKey();
		String subIdentName = elementEl.getSelectedValue();
		
		if(compensation == null) {
			compensation = disadvantageCompensationService.createDisadvantageCompensation(disadvantagedIdentity,
					extraTime, approvedBy, approval, getIdentity(), entry, subIdent, subIdentName);
			String afterXml = disadvantageCompensationService.toXml(compensation);
			disadvantageCompensationService.auditLog(Action.create, null, afterXml, compensation, getIdentity());
		} else {
			String beforeXml = disadvantageCompensationService.toXml(compensation);
			
			compensation.setApprovedBy(approvedBy);
			compensation.setApproval(approval);
			compensation.setExtraTime(extraTime);
			compensation.setSubIdent(subIdent);
			compensation.setSubIdentName(subIdentName);
			compensation = disadvantageCompensationService.updateDisadvantageCompensation(compensation);
			
			String afterXml = disadvantageCompensationService.toXml(compensation);
			disadvantageCompensationService.auditLog(Action.update, beforeXml, afterXml, compensation, getIdentity());
		}
		fireEvent(ureq, Event.DONE_EVENT);
	}

	@Override
	protected void formInnerEvent(UserRequest ureq, FormItem source, FormEvent event) {
		if(selectEntryButton == source) {
			doSelectEntry(ureq);
		}
		super.formInnerEvent(ureq, source, event);
	}

	@Override
	protected void formCancelled(UserRequest ureq) {
		fireEvent(ureq, Event.CANCELLED_EVENT);
	}
	
	private void doSelectEntry(UserRequest ureq) {
		if(guardModalController(searchFormCtrl)) return;

		searchFormCtrl = new ReferencableEntriesSearchController(getWindowControl(), ureq, 
					CourseModule.ORES_TYPE_COURSE, translate("select.form"));
		listenTo(searchFormCtrl);
		cmc = new CloseableModalController(getWindowControl(), translate("close"), searchFormCtrl.getInitialComponent(),
				true, translate("select.entry"));
		cmc.suppressDirtyFormWarningOnClose();
		listenTo(cmc);
		cmc.activate();
	}
	
	private void doSelectForm(RepositoryEntry selectedEntry) {
		this.entry = selectedEntry;
		selectRepositoryEntryLayout.contextPut("entryName", selectedEntry.getDisplayname());
		updateElementSelection(selectedEntry);	
	}
	
	private void updateElementSelection(RepositoryEntry selectedEntry) {
		final KeyValues values = new KeyValues();
		Visitor visitor = node -> {
			if(node instanceof IQTESTCourseNode) {
				IQTESTCourseNode testNode = (IQTESTCourseNode)node;
				if(testNode.hasQTI21TimeLimit(testNode.getCachedReferencedRepositoryEntry())) {
					values.add(KeyValues.entry(testNode.getIdent(), testNode.getShortTitle()));
				}
			}
		};
		
		ICourse course = CourseFactory.loadCourse(selectedEntry);
		CourseNode rootNode = course.getRunStructure().getRootNode();
		new TreeVisitor(visitor, rootNode, false)
			.visitAll();
		elementEl.setKeysAndValues(values.keys(), values.values(), null);
	}
}
