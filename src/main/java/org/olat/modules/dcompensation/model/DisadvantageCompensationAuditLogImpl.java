package org.olat.modules.dcompensation.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.olat.core.id.Persistable;
import org.olat.modules.dcompensation.DisadvantageCompensationAuditLog;

/**
 * 
 * Initial date: 24 sept. 2020<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
@Entity(name="dcompensationauditlog")
@Table(name="o_as_compensation_log")
public class DisadvantageCompensationAuditLogImpl implements DisadvantageCompensationAuditLog, Persistable {

	private static final long serialVersionUID = -3282282979096113055L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name="id", nullable=false, unique=true, insertable=true, updatable=false)
	private Long key;
	
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name="creationdate", nullable=false, insertable=true, updatable=false)
	private Date creationDate;
	
	@Column(name="a_action", nullable=true, insertable=true, updatable=false)
	private String action;	
	@Column(name="a_val_before", nullable=true, insertable=true, updatable=false)
	private String before;	
	@Column(name="a_val_after", nullable=true, insertable=true, updatable=false)
	private String after;
	
	@Column(name="a_subident", nullable=true, insertable=true, updatable=false)
	private String subIdent;
	
	@Column(name="fk_entry_id", nullable=true, insertable=true, updatable=false)
	private Long entryKey;
	@Column(name="fk_identity_id", nullable=true, insertable=true, updatable=false)
	private Long identityKey;
	@Column(name="fk_compensation_id", nullable=true, insertable=true, updatable=false)
	private Long compensationKey;
	@Column(name="fk_author_id", nullable=true, insertable=true, updatable=false)
	private Long authorKey;
	
	@Override
	public Long getKey() {
		return key;
	}

	public void setKey(Long key) {
		this.key = key;
	}

	@Override
	public Date getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	@Override
	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	@Override
	public String getBefore() {
		return before;
	}

	public void setBefore(String before) {
		this.before = before;
	}

	@Override
	public String getAfter() {
		return after;
	}

	public void setAfter(String after) {
		this.after = after;
	}

	public String getSubIdent() {
		return subIdent;
	}

	public void setSubIdent(String subIdent) {
		this.subIdent = subIdent;
	}

	public Long getEntryKey() {
		return entryKey;
	}

	public void setEntryKey(Long entryKey) {
		this.entryKey = entryKey;
	}

	public Long getCompensationKey() {
		return compensationKey;
	}

	public void setCompensationKey(Long compensationKey) {
		this.compensationKey = compensationKey;
	}

	public Long getAuthorKey() {
		return authorKey;
	}

	public void setAuthorKey(Long authorKey) {
		this.authorKey = authorKey;
	}

	public Long getIdentityKey() {
		return identityKey;
	}

	public void setIdentityKey(Long identityKey) {
		this.identityKey = identityKey;
	}

	@Override
	public int hashCode() {
		return key == null ? -864687 : key.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) {
			return true;
		}
		if(obj instanceof DisadvantageCompensationAuditLogImpl) {
			DisadvantageCompensationAuditLogImpl auditLog = (DisadvantageCompensationAuditLogImpl)obj;
			return getKey() != null && getKey().equals(auditLog.getKey());
		}
		return false;
	}

	@Override
	public boolean equalsByPersistableKey(Persistable persistable) {
		return equals(persistable);
	}
}
