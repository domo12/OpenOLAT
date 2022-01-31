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
package org.olat.course.assessment.ui.tool;

import java.util.List;

import org.olat.core.commons.fullWebApp.LayoutMain3ColsController;
import org.olat.core.gui.UserRequest;
import org.olat.core.gui.components.Component;
import org.olat.core.gui.components.Container;
import org.olat.core.gui.components.panel.Panel;
import org.olat.core.gui.components.stack.TooledStackedPanel;
import org.olat.core.gui.components.tree.GenericTreeModel;
import org.olat.core.gui.components.tree.GenericTreeNode;
import org.olat.core.gui.components.tree.MenuTree;
import org.olat.core.gui.components.tree.TreeModel;
import org.olat.core.gui.components.tree.TreeNode;
import org.olat.core.gui.control.Controller;
import org.olat.core.gui.control.Event;
import org.olat.core.gui.control.WindowControl;
import org.olat.core.gui.control.controller.BasicController;
import org.olat.core.gui.control.generic.dtabs.Activateable2;
import org.olat.core.id.OLATResourceable;
import org.olat.core.id.context.BusinessControlFactory;
import org.olat.core.id.context.ContextEntry;
import org.olat.core.id.context.StateEntry;
import org.olat.core.util.resource.OresHelper;
import org.olat.core.util.tree.TreeHelper;
import org.olat.course.CourseFactory;
import org.olat.course.ICourse;
import org.olat.course.assessment.AssessmentHelper;
import org.olat.course.assessment.CourseAssessmentService;
import org.olat.course.nodes.CourseNode;
import org.olat.course.run.userview.UserCourseEnvironment;
import org.olat.modules.assessment.ui.AssessmentToolContainer;
import org.olat.modules.assessment.ui.AssessmentToolSecurityCallback;
import org.olat.repository.RepositoryEntry;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * Initial date: 13.08.2016<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
public class AssessmentCourseTreeController extends BasicController implements Activateable2 {
	
	private final Panel mainPanel;
	private final MenuTree overviewMenuTree;
	private final GenericTreeNode overviewNode;
	private final MenuTree menuTree;
	private final TooledStackedPanel stackPanel;

	private AssessmentCourseOverviewController overviewCtrl;
	private AssessmentCourseNodeController identityListCtrl; 
	
	private final RepositoryEntry courseEntry;
	private final UserCourseEnvironment coachCourseEnv;
	private final AssessmentToolContainer toolContainer;
	private final AssessmentToolSecurityCallback assessmentCallback;
	private final String rootCourseNodeIdent;
	
	@Autowired
	private CourseAssessmentService courseAssessmentService;
	
	public AssessmentCourseTreeController(UserRequest ureq, WindowControl wControl, TooledStackedPanel stackPanel,
			RepositoryEntry courseEntry, UserCourseEnvironment coachCourseEnv,
			AssessmentToolContainer toolContainer, AssessmentToolSecurityCallback assessmentCallback) {
		super(ureq, wControl);
		this.stackPanel = stackPanel;
		this.courseEntry = courseEntry;
		this.toolContainer = toolContainer;
		this.coachCourseEnv = coachCourseEnv;
		this.assessmentCallback = assessmentCallback;
		
		stackPanel.addListener(this);
		
		ICourse course = CourseFactory.loadCourse(courseEntry);
		rootCourseNodeIdent = course.getRunStructure().getRootNode().getIdent();
		
		// Overview navigation
		overviewMenuTree = new MenuTree("menuTree");
		GenericTreeModel overviewTreeModel = new GenericTreeModel();
		overviewNode = new GenericTreeNode();
		overviewNode.setTitle(translate("assessment.tool.overview"));
		overviewNode.setIconCssClass("o_icon_assessment_tool");
		overviewTreeModel.setRootNode(overviewNode);
		overviewMenuTree.setTreeModel(overviewTreeModel);
		overviewMenuTree.setSelectedNodeId(overviewNode.getIdent());
		overviewMenuTree.addListener(this);

		// Navigation menu
		menuTree = new MenuTree("menuTree");
		TreeModel tm = AssessmentHelper.assessmentTreeModel(course);
		menuTree.setTreeModel(tm);
		menuTree.setSelectedNode(tm.getRootNode());
		menuTree.setHighlightSelection(false);
		menuTree.addListener(this);
		
		Container menuCont = createVelocityContainer("menu");
		menuCont.put("overview", overviewMenuTree);
		menuCont.put("nodes", menuTree);
		
		mainPanel = new Panel("empty");
		LayoutMain3ColsController columLayoutCtr = new LayoutMain3ColsController(ureq, getWindowControl(), menuCont, mainPanel, "course" + course.getResourceableId());
		listenTo(columLayoutCtr); // cleanup on dispose
		putInitialPanel(columLayoutCtr.getInitialComponent());
	}
	
	public String getRootNodeId() {
		return rootCourseNodeIdent;
	}
	
	@Override
	protected void doDispose() {
		if(stackPanel != null) {
			stackPanel.removeListener(this);
		}
        super.doDispose();
	}

	@Override
	public void activate(UserRequest ureq, List<ContextEntry> entries, StateEntry state) {
		if(entries != null && !entries.isEmpty()) {
			ContextEntry entry = entries.get(0);
			String resourceTypeName = entry.getOLATResourceable().getResourceableTypeName();
			if("Identity".equalsIgnoreCase(resourceTypeName)) {
				TreeNode treeNode =  menuTree.getTreeModel().getRootNode();
				CourseNode courseNode = (CourseNode)treeNode.getUserObject();
				if(courseNode != null) {
					AssessmentCourseNodeController ctrl = doSelectCourseNode(ureq, treeNode, courseNode);
					if(ctrl != null) {
						ctrl.activate(ureq, entries, null);
					}
					menuTree.setSelectedNode(treeNode);
				}
			} else if("Node".equalsIgnoreCase(resourceTypeName) || "CourseNode".equalsIgnoreCase(resourceTypeName)) {
				Long nodeIdent = entries.get(0).getOLATResourceable().getResourceableId();
				CourseNode courseNode = CourseFactory.loadCourse(courseEntry).getRunStructure().getNode(nodeIdent.toString());
				TreeNode treeNode = TreeHelper.findNodeByUserObject(courseNode, menuTree.getTreeModel().getRootNode());
				if(courseNode != null) {
					AssessmentCourseNodeController ctrl = doSelectCourseNode(ureq, treeNode, courseNode);
					if(ctrl != null) {
						List<ContextEntry> subEntries = entries.subList(1, entries.size());
						ctrl.activate(ureq, subEntries, state);
					}
					menuTree.setSelectedNode(treeNode);
				}
			} else if ("Overview".equalsIgnoreCase(resourceTypeName)) {
				doShowOverview(ureq);
			}
		} else {
			doShowOverview(ureq);
		}
	}

	@Override
	protected void event(UserRequest ureq, Component source, Event event) {
		if (source == overviewMenuTree) {
			if (event.getCommand().equals(MenuTree.COMMAND_TREENODE_CLICKED)) {
				TreeNode selectedTreeNode = overviewMenuTree.getSelectedNode();
				if (selectedTreeNode == overviewNode) {
					doShowOverview(ureq);
				}
			}
		} else if (source == menuTree) {
			if (event.getCommand().equals(MenuTree.COMMAND_TREENODE_CLICKED)) {
				TreeNode selectedTreeNode = menuTree.getSelectedNode();
				Object uo = selectedTreeNode.getUserObject();
				if(uo instanceof CourseNode) {
					processSelectCourseNodeWithMemory(ureq, selectedTreeNode, (CourseNode)uo);
				}
			}
		}
	}
	
	@Override
	protected void event(UserRequest ureq, Controller source, Event event) {
		if (source == overviewCtrl) {
			fireEvent(ureq, event);
		}
		super.event(ureq, source, event);
	}

	private void processSelectCourseNodeWithMemory(UserRequest ureq, TreeNode tn, CourseNode cn) {
		StateEntry listState = identityListCtrl != null? identityListCtrl.getListState(): null;
		AssessmentCourseNodeController ctrl = doSelectCourseNode(ureq, tn, cn);
		if(ctrl != null) {
			ctrl.activate(ureq, null, listState);
		}
	}

	private AssessmentCourseNodeController doSelectCourseNode(UserRequest ureq, TreeNode treeNode, CourseNode courseNode) {
		overviewMenuTree.setHighlightSelection(false);
		menuTree.setHighlightSelection(true);
		
		stackPanel.popUpToController(this);
		stackPanel.changeDisplayname(treeNode.getTitle(), "o_icon " + treeNode.getIconCssClass(), this);

		removeAsListenerAndDispose(identityListCtrl);
		
		OLATResourceable oresNode = OresHelper.createOLATResourceableInstance("Node", Long.valueOf(courseNode.getIdent()));
		WindowControl bwControl = BusinessControlFactory.getInstance().createBusinessWindowControl(oresNode, null, getWindowControl());
		identityListCtrl = courseAssessmentService.getIdentityListController(ureq, bwControl, stackPanel, courseNode, courseEntry,
				coachCourseEnv, toolContainer, assessmentCallback, true);
		if(identityListCtrl == null) {
			mainPanel.setContent(new Panel("empty"));
		} else {
			listenTo(identityListCtrl);
			mainPanel.setContent(identityListCtrl.getInitialComponent());
			addToHistory(ureq, identityListCtrl);
		}
		return identityListCtrl;
	}
	
	private void doShowOverview(UserRequest ureq) {
		overviewMenuTree.setHighlightSelection(true);
		menuTree.setHighlightSelection(false);
		
		stackPanel.popUpToController(this);
		stackPanel.changeDisplayname(translate("assessment.tool.overview"), "o_icon o_icon_assessment_tool", this);
		
		removeAsListenerAndDispose(overviewCtrl);
		
		OLATResourceable oresNode = OresHelper.createOLATResourceableInstance("Overview", Long.valueOf(0));
		WindowControl bwControl = BusinessControlFactory.getInstance().createBusinessWindowControl(oresNode, null, getWindowControl());
		overviewCtrl = new AssessmentCourseOverviewController(ureq, bwControl, courseEntry, coachCourseEnv, assessmentCallback);
		listenTo(overviewCtrl);
		mainPanel.setContent(overviewCtrl.getInitialComponent());
		addToHistory(ureq, overviewCtrl);
	}

	public void reloadAssessmentModes() {
		if (overviewCtrl != null) {
			overviewCtrl.reloadAssessmentModes();
		}
	}
	
}