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

package com.liferay.document.selector.impl;

import com.liferay.document.selector.ItemSelector;
import com.liferay.document.selector.ItemSelectorCriterion;
import com.liferay.document.selector.ItemSelectorCriterionHandler;
import com.liferay.document.selector.ItemSelectorView;
import com.liferay.document.selector.ItemSelectorViewRenderer;
import com.liferay.document.selector.web.constants.DocumentSelectorPortletKeys;
import com.liferay.document.selector.web.portlet.DocumentSelectorPortlet;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.portlet.LiferayWindowState;
import com.liferay.portal.kernel.util.Accessor;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portlet.PortletURLFactoryUtil;

import java.lang.reflect.InvocationTargetException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.portlet.ActionRequest;
import javax.portlet.PortletMode;
import javax.portlet.PortletModeException;
import javax.portlet.PortletRequest;
import javax.portlet.PortletURL;
import javax.portlet.WindowStateException;

import org.apache.commons.beanutils.BeanUtils;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

/**
 * @author Iván Zaera
 */
@Component(service = ItemSelector.class)
public class ItemSelectorImpl implements ItemSelector {

	public static final String PARAM_CRITERIA = "criteria";

	@Override
	public PortletURL getItemSelectorURL(
		PortletRequest portletRequest,
		ItemSelectorCriterion... itemSelectorCriteria) {

		Map<String, String[]> parameters = getItemSelectorParameters(
			itemSelectorCriteria);

		ThemeDisplay themeDisplay = (ThemeDisplay)portletRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		PortletURL portletURL = PortletURLFactoryUtil.create(
			portletRequest, DocumentSelectorPortletKeys.DOCUMENT_SELECTOR,
			themeDisplay.getPlid(), PortletRequest.ACTION_PHASE);

		_setPopup(portletURL);

		portletURL.setParameter(
			ActionRequest.ACTION_NAME,
			DocumentSelectorPortlet.ACTION_SHOW_ITEM_SELECTOR);

		for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
			portletURL.setParameter(entry.getKey(), entry.getValue());
		}

		return portletURL;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public List<ItemSelectorViewRenderer<?>> getItemSelectorViewRenderers(
		Map<String, String[]> parameters) {

		List<ItemSelectorViewRenderer<?>> itemSelectorViewRenderers =
			new ArrayList<>();

		List<Class<? extends ItemSelectorCriterion>>
			itemSelectorCriterionClasses = _getItemSelectorCriterionClasses(
				parameters);

		for (int i = 0; i<itemSelectorCriterionClasses.size(); i++) {
			Class<? extends ItemSelectorCriterion> itemSelectorCriterionClass =
				itemSelectorCriterionClasses.get(i);

			String paramPrefix = i + "_";

			_addItemSelectorViewRenderers(
				(List)itemSelectorViewRenderers, parameters, paramPrefix,
				itemSelectorCriterionClass);
		}

		return itemSelectorViewRenderers;
	}

	protected void _setPopup(PortletURL portletURL) {
		try {
			portletURL.setPortletMode(PortletMode.VIEW);
			portletURL.setWindowState(LiferayWindowState.POP_UP);
		}
		catch (PortletModeException pme) {
			throw new SystemException(pme);
		}
		catch (WindowStateException wse) {
			throw new SystemException(wse);
		}
	}

	protected Map<String, String[]> getItemSelectorParameters(
		ItemSelectorCriterion... itemSelectorCriteria) {

		Map<String, String[]> parameters = new HashMap<>();

		_populateCriteria(parameters, itemSelectorCriteria);

		for (int i = 0; i < itemSelectorCriteria.length; i++) {
			ItemSelectorCriterion itemSelectorCriterion =
				itemSelectorCriteria[i];

			String paramPrefix = i + "_";

			_populateDesiredReturnTypes(
				parameters, paramPrefix, itemSelectorCriterion);

			_populateItemSelectorCriteria(
				parameters, paramPrefix, itemSelectorCriterion);
		}

		return parameters;
	}

	@Reference(
		cardinality = ReferenceCardinality.MULTIPLE,
		policy = ReferencePolicy.DYNAMIC
	)
	protected <T extends ItemSelectorCriterion> void
		setItemSelectionCriterionHandler(
			ItemSelectorCriterionHandler<T> itemSelectionCriterionHandler) {

		Class<T> itemSelectorCriterionClass =
			itemSelectionCriterionHandler.getItemSelectorCriterionClass();

		_itemSelectionCriterionHandlers.put(
			itemSelectorCriterionClass.getName(),
			itemSelectionCriterionHandler);
	}

	protected <T extends ItemSelectorCriterion>
		void unsetItemSelectionCriterionHandler(
			ItemSelectorCriterionHandler<T> itemSelectionCriterionHandler) {

		Class<T> itemSelectorCriterionClass =
			itemSelectionCriterionHandler.getItemSelectorCriterionClass();

		_itemSelectionCriterionHandlers.remove(
			itemSelectorCriterionClass.getName());
	}

	private <T extends ItemSelectorCriterion>
		void _addItemSelectorViewRenderers(
			List<ItemSelectorViewRenderer<T>> itemSelectorViewRenderers,
			Map<String, String[]> parameters, String paramPrefix,
			Class<T> itemSelectorCriterionClass) {

		ItemSelectorCriterionHandler<T> itemSelectorCriterionHandler =
			_getItemSelectorCriterionHandler(itemSelectorCriterionClass);

		T itemSelectorCriterion = _getItemSelectorCriterion(
			parameters, paramPrefix, itemSelectorCriterionClass);

		List<ItemSelectorView<T>> itemSelectorViews =
			itemSelectorCriterionHandler.getItemSelectorViews(
				itemSelectorCriterion);

		for (ItemSelectorView<T> itemSelectorView : itemSelectorViews) {
			itemSelectorViewRenderers.add(
				new ItemSelectorViewRenderer<T>(
					itemSelectorView, itemSelectorCriterion)
			);
		}
	}

	private <T extends ItemSelectorCriterion> T _getItemSelectorCriterion(
		Map<String, String[]> parameters, String paramPrefix,
		Class<T> itemSelectorCriterionClass) {

		try {
			T itemSelectorCriterion = itemSelectorCriterionClass.newInstance();

			Map<String, String> properties = new HashMap<>();

			for (String key : parameters.keySet()) {
				if (key.startsWith(paramPrefix)) {
					properties.put(
						key.substring(paramPrefix.length()),
						_getValue(parameters, key));
				}
			}

			BeanUtils.populate(itemSelectorCriterion, properties);

			return itemSelectorCriterion;
		}
		catch (InvocationTargetException | InstantiationException |
			IllegalAccessException e) {

			throw new SystemException(
				"Unable to unmarshall item selector criterion", e);
		}
	}

	private List<Class<? extends ItemSelectorCriterion>>
		_getItemSelectorCriterionClasses(Map<String, String[]> parameters) {

		String criteria = _getValue(parameters, PARAM_CRITERIA);

		String[] itemSelectorCriterionClassNames = criteria.split(",");

		List<Class<? extends ItemSelectorCriterion>>
			itemSelectorCriterionClasses = new ArrayList<>();

		for (String itemSelectorCriterionClassName :
				itemSelectorCriterionClassNames) {

			ItemSelectorCriterionHandler<?> itemSelectorCriterionHandler =
				_itemSelectionCriterionHandlers.get(
					itemSelectorCriterionClassName);

			itemSelectorCriterionClasses.add(
				itemSelectorCriterionHandler.getItemSelectorCriterionClass());
		}

		return itemSelectorCriterionClasses;
	}

	@SuppressWarnings("unchecked")
	private <T extends ItemSelectorCriterion> ItemSelectorCriterionHandler<T>
		_getItemSelectorCriterionHandler(Class<T> itemSelectorCriterionClass) {

		return (ItemSelectorCriterionHandler<T>)
			_itemSelectionCriterionHandlers.get(
				itemSelectorCriterionClass.getName());
	}

	private String _getValue(
		Map<String, String[]> parameters, String name) {

		String[] values = parameters.get(name);

		if (ArrayUtil.isEmpty(values)) {
			return StringPool.BLANK;
		}
		else {
			return values[0];
		}
	}

	private void _populateCriteria(
		Map<String, String[]> parameters,
		ItemSelectorCriterion[] itemSelectorCriteria) {

		Accessor<ItemSelectorCriterion, String> accessor =
			new Accessor<ItemSelectorCriterion, String>() {

				@Override
				public String get(ItemSelectorCriterion itemSelectorCriterion) {
					Class<?> clazz = itemSelectorCriterion.getClass();

					return clazz.getName();
				}

				@Override
				public Class<String> getAttributeClass() {
					return String.class;
				}

				@Override
				public Class<ItemSelectorCriterion> getTypeClass() {
					return ItemSelectorCriterion.class;
				}

			};

		parameters.put(
			PARAM_CRITERIA,
			new String[] {ArrayUtil.toString(itemSelectorCriteria, accessor)});
	}

	private void _populateDesiredReturnTypes(
		Map<String, String[]> parameters, String paramPrefix,
		ItemSelectorCriterion itemSelectorCriterion) {

		Set<Class<?>> desiredReturnTypes =
			itemSelectorCriterion.getDesiredReturnTypes();

		Set<Class<?>> availableReturnTypes =
			itemSelectorCriterion.getAvailableReturnTypes();

		if (desiredReturnTypes.size() == availableReturnTypes.size()) {
			return;
		}

		Accessor<Class<?>, String> accessor = new Accessor<Class<?>, String>() {

			@Override
			public String get(Class<?> clazz) {
				return clazz.getName();
			}

			@Override
			public Class<String> getAttributeClass() {
				return String.class;
			}

			@Override
			@SuppressWarnings("rawtypes")
			public Class<Class<?>> getTypeClass() {
				return (Class)Class.class;
			}

		};

		parameters.put(
			paramPrefix + "desiredReturnTypes",
			new String[] {
				ArrayUtil.toString(
					desiredReturnTypes.toArray(
						new Class<?>[desiredReturnTypes.size()]),
					accessor)
			});
	}

	@SuppressWarnings("unchecked")
	private void _populateItemSelectorCriteria(
		Map<String, String[]> parameters, String paramPrefix,
		ItemSelectorCriterion itemSelectorCriterion) {

		try {
			Map<String, ?> properties = BeanUtils.describe(
				itemSelectorCriterion);

			for (Map.Entry<String, ?> entry : properties.entrySet()) {
				String key = entry.getKey();

				if (key.equals("availableReturnTypes") || key.equals("class") ||
					key.equals("desiredReturnTypes")) {

					continue;
				}

				Object value = entry.getValue();

				parameters.put(
					paramPrefix + key, new String[] {value.toString()});
			}
		}
		catch (IllegalAccessException | InvocationTargetException |
			   NoSuchMethodException e) {

			throw new SystemException(
				"Unable to marshall item selector criterion", e);
		}
	}

	private final ConcurrentMap<String, ItemSelectorCriterionHandler<?>>
		_itemSelectionCriterionHandlers = new ConcurrentHashMap<>();

}