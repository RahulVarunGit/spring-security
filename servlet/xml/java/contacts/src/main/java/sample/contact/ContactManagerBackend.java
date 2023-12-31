/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample.contact;

import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.support.ApplicationObjectSupport;
import org.springframework.security.acls.domain.BasePermission;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.AccessControlEntry;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.MutableAclService;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.acls.model.Sid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Concrete implementation of {@link ContactManager}.
 *
 * @author Ben Alex
 */
@Transactional
public class ContactManagerBackend extends ApplicationObjectSupport implements ContactManager, InitializingBean {

	private ContactDao contactDao;

	private MutableAclService mutableAclService;

	private int counter = 1000;

	public void afterPropertiesSet() {
		Assert.notNull(this.contactDao, "contactDao required");
		Assert.notNull(this.mutableAclService, "mutableAclService required");
	}

	public void addPermission(Contact contact, Sid recipient, Permission permission) {
		MutableAcl acl;
		ObjectIdentity oid = new ObjectIdentityImpl(Contact.class, contact.getId());

		try {
			acl = (MutableAcl) this.mutableAclService.readAclById(oid);
		}
		catch (NotFoundException nfe) {
			acl = this.mutableAclService.createAcl(oid);
		}

		acl.insertAce(acl.getEntries().size(), permission, recipient, true);
		this.mutableAclService.updateAcl(acl);

		logger.debug("Added permission " + permission + " for Sid " + recipient + " contact " + contact);
	}

	public void create(Contact contact) {
		// Create the Contact itself
		contact.setId((long) this.counter++);
		this.contactDao.create(contact);

		// Grant the current principal administrative permission to the contact
		addPermission(contact, new PrincipalSid(getUsername()), BasePermission.ADMINISTRATION);

		if (logger.isDebugEnabled()) {
			logger.debug("Created contact " + contact + " and granted admin permission to recipient " + getUsername());
		}
	}

	public void delete(Contact contact) {
		this.contactDao.delete(contact.getId());

		// Delete the ACL information as well
		ObjectIdentity oid = new ObjectIdentityImpl(Contact.class, contact.getId());
		this.mutableAclService.deleteAcl(oid, false);

		if (logger.isDebugEnabled()) {
			logger.debug("Deleted contact " + contact + " including ACL permissions");
		}
	}

	public void deletePermission(Contact contact, Sid recipient, Permission permission) {
		ObjectIdentity oid = new ObjectIdentityImpl(Contact.class, contact.getId());
		MutableAcl acl = (MutableAcl) this.mutableAclService.readAclById(oid);

		// Remove all permissions associated with this particular recipient (string
		// equality to KISS)
		List<AccessControlEntry> entries = acl.getEntries();

		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).getSid().equals(recipient) && entries.get(i).getPermission().equals(permission)) {
				acl.deleteAce(i);
			}
		}

		this.mutableAclService.updateAcl(acl);

		if (logger.isDebugEnabled()) {
			logger.debug("Deleted contact " + contact + " ACL permissions for recipient " + recipient);
		}
	}

	@Transactional(readOnly = true)
	public List<Contact> getAll() {
		logger.debug("Returning all contacts");

		return this.contactDao.findAll();
	}

	@Transactional(readOnly = true)
	public List<String> getAllRecipients() {
		logger.debug("Returning all recipients");

		return this.contactDao.findAllPrincipals();
	}

	@Transactional(readOnly = true)
	public Contact getById(Long id) {
		if (logger.isDebugEnabled()) {
			logger.debug("Returning contact with id: " + id);
		}

		return this.contactDao.getById(id);
	}

	@Transactional(readOnly = true)
	public Contact getRandomContact() {
		logger.debug("Returning random contact");

		Random rnd = new Random();
		List<Contact> contacts = this.contactDao.findAll();
		int getNumber = rnd.nextInt(contacts.size());

		return contacts.get(getNumber);
	}

	protected String getUsername() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth.getPrincipal() instanceof UserDetails) {
			return ((UserDetails) auth.getPrincipal()).getUsername();
		}
		else {
			return auth.getPrincipal().toString();
		}
	}

	public void setContactDao(ContactDao contactDao) {
		this.contactDao = contactDao;
	}

	public void setMutableAclService(MutableAclService mutableAclService) {
		this.mutableAclService = mutableAclService;
	}

	public void update(Contact contact) {
		this.contactDao.update(contact);

		logger.debug("Updated contact " + contact);
	}

}
