/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.knowledge.base.model.impl;

import aQute.bnd.annotation.ProviderType;

import com.liferay.knowledge.base.constants.KBFolderConstants;
import com.liferay.knowledge.base.model.KBFolder;
import com.liferay.knowledge.base.service.KBArticleServiceUtil;
import com.liferay.knowledge.base.service.KBFolderServiceUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.util.Locale;

/**
 * @author Brian Wing Shun Chan
 */
@ProviderType
public class KBFolderImpl extends KBFolderBaseImpl {

	public KBFolderImpl() {
	}

	@Override
	public long getClassNameId() {
		if (_classNameId == 0) {
			_classNameId = PortalUtil.getClassNameId(
				KBFolderConstants.getClassName());
		}

		return _classNameId;
	}

	@Override
	public String getParentTitle(Locale locale) throws PortalException {
		if (getParentKBFolderId() ==
				KBFolderConstants.DEFAULT_PARENT_FOLDER_ID) {

			return LanguageUtil.get(locale, "home");
		}

		KBFolder kbFolder = KBFolderServiceUtil.getKBFolder(
			getParentKBFolderId());

		return kbFolder.getName();
	}

	@Override
	public boolean isEmpty() throws PortalException {
		int kbArticlesCount = KBArticleServiceUtil.getKBArticlesCount(
			getGroupId(), getKbFolderId(), WorkflowConstants.STATUS_ANY);

		if (kbArticlesCount > 0) {
			return false;
		}

		int kbFoldersCount = KBFolderServiceUtil.getKBFoldersCount(
			getGroupId(), getKbFolderId());

		if (kbFoldersCount > 0) {
			return false;
		}

		return true;
	}

	private long _classNameId;

}