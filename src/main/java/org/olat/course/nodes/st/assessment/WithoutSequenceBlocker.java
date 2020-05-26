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
package org.olat.course.nodes.st.assessment;

import java.util.Date;

import org.olat.course.run.scoring.Blocker;

/**
 * 
 * Initial date: 3 Feb 2020<br>
 * @author uhensler, urs.hensler@frentix.com, http://www.frentix.com
 *
 */
public class WithoutSequenceBlocker implements Blocker {

	private final Blocker parent;
	private boolean blocked;
	private Date startDate;
	
	public WithoutSequenceBlocker(Blocker parent) {
		this.parent = parent;
	}

	@Override
	public boolean isBlocked() {
		return blocked
				? true 
				: parent != null? parent.isBlocked(): false;
	}

	@Override
	public void block() {
		blocked = true;
	}
	
	@Override
	public void block(Date startDate) {
		this.startDate = startDate;
		block();
	}

	@Override
	public Date getStartDate() {
		return startDate != null
				? startDate
				: parent != null? parent.getStartDate(): null;
	}

	@Override
	public void nextCourseNode() {
		blocked = false;
		startDate = null;
	}

}
