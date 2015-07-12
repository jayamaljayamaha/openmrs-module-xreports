package org.openmrs.module.xreports.page.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.xreports.XReport;
import org.openmrs.module.xreports.XReportGroup;
import org.openmrs.module.xreports.api.XReportsService;
import org.openmrs.ui.framework.SimpleObject;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class RunReportsPageController {

	public void controller(PageModel model,
			@RequestParam(required = false, value = "groupId") Integer groupId,
			@RequestParam(required = false, value = "reportTitle") String reportTitle,
			UiSessionContext emrContext, UiUtils ui) {

		emrContext.requireAuthentication();
		
		List<XReport> reports = Context.getService(XReportsService.class).getReports(groupId);
		List<XReportGroup> groups = Context.getService(XReportsService.class).getReportGroups(groupId);
		
		model.addAttribute("reports", reports);
		model.addAttribute("groups", groups);
		
		model.addAttribute("breadcrumbOverride", generateBreadcrumbOverride(groupId, ui));
	}
	
	protected String generateBreadcrumbOverride(String breadcrumbOverrideLabel, String breadcrumbOverrideProvider,
            String breadcrumbOverridePage, String appId, UiUtils ui) {
		Map<String, Object> attrs = new HashMap<String,Object>();
		if (StringUtils.isNotBlank(appId)) {
			// TODO super hack that we add this twice, but the registration app seems to go with the model of calling the requesat param "appId", while other modules us "app"
			attrs.put("appId", appId);
			attrs.put("app", appId);
		}
		SimpleObject breadcrumbOverride = SimpleObject.create("label", ui.message(breadcrumbOverrideLabel), "link",
		ui.pageLink(breadcrumbOverrideProvider, breadcrumbOverridePage, attrs));
		return ui.toJson(breadcrumbOverride);
	}
	
	protected String generateBreadcrumbOverride(Integer groupId, UiUtils ui) {
		
		if (groupId == null) {
			return null;
		}
		
		XReportGroup group = Context.getService(XReportsService.class).getReportGroup(groupId);
		
		String breadcrumbOverrideLabel = group.getName();
		String breadcrumbOverrideProvider = "xreports";
		String breadcrumbOverridePage = "runReports";

		Map<String, Object> attrs = new HashMap<String,Object>();

		SimpleObject breadcrumbOverride = new SimpleObject();
		breadcrumbOverride.put("label", ui.message(breadcrumbOverrideLabel));
		breadcrumbOverride.put("link", ui.pageLink(breadcrumbOverrideProvider, breadcrumbOverridePage, attrs));
		
		breadcrumbOverride.put("label2", ui.message(breadcrumbOverrideLabel));
		breadcrumbOverride.put("link2", ui.pageLink(breadcrumbOverrideProvider, breadcrumbOverridePage, attrs));
		
		/*SimpleObject breadcrumbOverride = SimpleObject.create("label", ui.message(breadcrumbOverrideLabel), "link",
		ui.pageLink(breadcrumbOverrideProvider, breadcrumbOverridePage, attrs),
		"label", ui.message("test"), "link",
		ui.pageLink(breadcrumbOverrideProvider, breadcrumbOverridePage, attrs));*/
		
		return ui.toJson(breadcrumbOverride);
	}
}
