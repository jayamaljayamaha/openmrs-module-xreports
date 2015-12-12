package org.openmrs.module.xreports.web.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAttributeType;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.dataset.DataSetColumn;
import org.openmrs.module.reporting.dataset.definition.CohortCrossTabDataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.CohortCrossTabDataSetDefinition.CohortDataSetColumn;
import org.openmrs.module.reporting.dataset.definition.CohortIndicatorAndDimensionDataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.CohortIndicatorAndDimensionDataSetDefinition.CohortIndicatorAndDimensionSpecification;
import org.openmrs.module.reporting.dataset.definition.CohortIndicatorDataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.CohortIndicatorDataSetDefinition.CohortIndicatorAndDimensionColumn;
import org.openmrs.module.reporting.dataset.definition.CohortsWithVaryingParametersDataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.CohortsWithVaryingParametersDataSetDefinition.Column;
import org.openmrs.module.reporting.dataset.definition.DataExportDataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.DataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.LogicDataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.RowPerObjectDataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.SimpleIndicatorDataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.SimpleIndicatorDataSetDefinition.SimpleIndicatorColumn;
import org.openmrs.module.reporting.dataset.definition.SimplePatientDataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.SqlDataSetDefinition;
import org.openmrs.module.reporting.evaluation.parameter.Mapped;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.xreports.DOMUtil;
import org.openmrs.module.xreports.DesignItem;
import org.openmrs.module.xreports.XReport;
import org.openmrs.module.xreports.XReportsConstants;
import org.openmrs.module.xreports.api.XReportsService;
import org.openmrs.module.xreports.web.PdfDocument;
import org.openmrs.module.xreports.web.ReportBuilder;
import org.openmrs.module.xreports.web.ReportCommandObject;
import org.openmrs.module.xreports.web.util.WebUtil;
import org.openmrs.reporting.export.ExportColumn;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ReportDownloadServlet extends HttpServlet {
	
	public static final long serialVersionUID = 1L;
	
	private Log log = LogFactory.getLog(this.getClass());
	
	/** The formId request parameter. */
	public static final String REQUEST_PARAM_REPORT_ID = "formId";
	
	private List<String> idlist;
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		PrintWriter writer = null;
		
		if (!"true".equals(request.getParameter("renderer"))) {
			writer = response.getWriter();
		}
		
		try {
			//try to authenticate users who log on inline (with the request).
			try {
				WebUtil.authenticateInlineUser(request);
			}
			catch (ContextAuthenticationException e) {
				log.error(e.getMessage(), e);
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			
			if (!WebUtil.isAuthenticated(request, response, null)) {
				return;
			}
			
			response.setHeader(XReportsConstants.HTTP_HEADER_CONTENT_TYPE, XReportsConstants.HTTP_HEADER_CONTENT_TYPE_XML);
			response.setCharacterEncoding(XReportsConstants.DEFAULT_CHARACTER_ENCODING);
			
			Integer reportId = Integer.parseInt(request.getParameter(REQUEST_PARAM_REPORT_ID));
			XReport report = Context.getService(XReportsService.class).getReport(reportId);
			String xml = report.getXml();

			if ("true".equals(request.getParameter("runner"))) {
				if (StringUtils.isNotBlank(xml)) {
					ReportCommandObject reportParamData = (ReportCommandObject)request.getSession().getAttribute(XReportsConstants.REPORT_PARAMETER_DATA);
					xml = new ReportBuilder().build(xml, request.getQueryString(), report, reportParamData);
				}
			}
			else if ("true".equals(request.getParameter("renderer"))) {
				ReportCommandObject reportParamData = (ReportCommandObject)request.getSession().getAttribute(XReportsConstants.REPORT_PARAMETER_DATA);

				String filename = DateUtil.formatDate(new Date(), "yyyy-MM-dd-HHmmss");
				filename = reportParamData.getReportDefinition().getName() + "_" + filename + ".pdf";;
	            
				response.setHeader(XReportsConstants.HTTP_HEADER_CONTENT_DISPOSITION, 
						XReportsConstants.HTTP_HEADER_CONTENT_DISPOSITION_VALUE + WebUtil.getXmlToken(filename));
				response.setContentType(XReportsConstants.CONTENT_TYPE_PDF);
				
				response.setHeader("Cache-Control", "no-cache");
				response.setHeader("Pragma", "no-cache");
				response.setDateHeader("Expires", -1);
				response.setHeader("Cache-Control", "no-store");
				response.setCharacterEncoding(XReportsConstants.DEFAULT_CHARACTER_ENCODING);
				
				new PdfDocument().writeFromXml(response.getOutputStream(), new ReportBuilder().build(xml, request.getQueryString(), report, reportParamData), request.getRealPath(""));
				
				return;
			}
			else {
				String uuid = report.getExternalReportUuid();
				if (uuid != null) {
					ReportDefinition reportDef = Context.getService(ReportDefinitionService.class).getDefinitionByUuid(uuid);
					if (xml == null) {
						xml = "";
					}
					
					Document doc = DOMUtil.fromString2Doc(xml);
					xml = mergeDesignItems(doc, getDesignItems(reportDef, doc));
				}
			}
			
			if (StringUtils.isBlank(xml)) {
				xml = " ";
			}
			
			writer.print(xml);
		}
		catch (Exception ex) {
			WebUtil.reportError(ex, request, response, writer);
		}
	}
	
	private String getDesignItems(ReportDefinition reportDef, Document doc) {
		if (reportDef == null) {
			return "";
		}
		
		Map<String, Element> map = getItemBindingMap(doc);
		
		int id = 0;
		String xml = "<DesignItems>";
		
		for (Map.Entry<String, Mapped<? extends DataSetDefinition>> e : reportDef.getDataSetDefinitions().entrySet()) {
			DataSetDefinition def = e.getValue().getParameterizable();
			
			id = getNextId(id);
			xml += "<DesignItem type='0' binding='" + e.getKey() + "' id='" + id + "' name='" + def.getName() + "' description='" + def.getDescription() + "' sourceType='Grouping' >";
			
			if (def instanceof SimplePatientDataSetDefinition) {
				
				for (String property : ((SimplePatientDataSetDefinition) def).getPatientProperties()) {
					id = getNextId(id);
					xml += "<DesignItem type='" + DesignItem.X_POS + "' id='" + id +"' name='" + property + "' binding='" + property + "' text='" + property + "' sourceType='Custom' />";
				}
				for (PersonAttributeType attribute : ((SimplePatientDataSetDefinition) def).getPersonAttributeTypes()) {
					id = getNextId(id);
					String property = StringEscapeUtils.escapeXml(attribute.getName());
					xml += "<DesignItem type='" + DesignItem.X_POS + "' id='" + id +"' name='" + property + "' binding='" + attribute.getId() + "' text='" + property + "' sourceType='Custom' />";
				}
				for (PatientIdentifierType identifier : ((SimplePatientDataSetDefinition) def).getIdentifierTypes()) {
					id = getNextId(id);
					String property = StringEscapeUtils.escapeXml(identifier.getName());
					xml += "<DesignItem type='" + DesignItem.X_POS + "' id='" + id +"' name='" + property + "' binding='" + identifier.getId() + "' text='" + property + "' sourceType='Custom' />";
				}
			}
			else if (def instanceof CohortIndicatorDataSetDefinition) {
				for (CohortIndicatorAndDimensionColumn col : ((CohortIndicatorDataSetDefinition) def).getColumns()) {
					id = getNextId(id);
					xml += "<DesignItem type='" + DesignItem.PT_POS + "' id='" + id +"' name='" + col.getLabel() + "' binding='" + col.getName() + "' text='" + col.getName() + "' sourceType='Custom' />";
				}
			}
			else if (def instanceof CohortCrossTabDataSetDefinition) {
				for (CohortDataSetColumn col : ((CohortCrossTabDataSetDefinition) def).getDataSetColumns()) {
					id = getNextId(id);
					xml += "<DesignItem type='" + DesignItem.PT_POS + "' id='" + id +"' name='" + col.getLabel() + "' binding='" + col.getName() + "' text='" + col.getName() + "' sourceType='Custom' />";
				}
			}
			else if (def instanceof CohortIndicatorAndDimensionDataSetDefinition) {
				for (CohortIndicatorAndDimensionSpecification col : ((CohortIndicatorAndDimensionDataSetDefinition) def).getSpecifications()) {
					id = getNextId(id);
					xml += "<DesignItem type='" + DesignItem.PT_POS + "' id='" + id +"' name='" + col.getLabel() + "' binding='" + col.getIndicatorNumber() + "' text='" + col.getLabel() + "' sourceType='Custom' />";
				}
			}
			else if (def instanceof CohortsWithVaryingParametersDataSetDefinition) {
				for (Column col : ((CohortsWithVaryingParametersDataSetDefinition) def).getColumns()) {
					id = getNextId(id);
					xml += "<DesignItem type='" + DesignItem.PT_POS + "' id='" + id +"' name='" + col.getLabel() + "' binding='" + col.getName() + "' text='" + col.getLabel() + "' sourceType='Custom' />";
				}
			}
			else if (def instanceof DataExportDataSetDefinition) {
				for (ExportColumn col : ((DataExportDataSetDefinition) def).getDataExport().getColumns()) {
					id = getNextId(id);
					xml += "<DesignItem type='" + DesignItem.X_POS + "' id='" + id +"' name='" + col.getColumnName() + "' binding='" + col.getColumnName() + "' text='" + col.getColumnName() + "' sourceType='Custom' />";
				}
			}
			else if (def instanceof LogicDataSetDefinition) {
				for (LogicDataSetDefinition.Column col : ((LogicDataSetDefinition) def).getColumns()) {
					id = getNextId(id);
					xml += "<DesignItem type='" + DesignItem.PT_POS + "' id='" + id +"' name='" + col.getLabel() + "' binding='" + col.getName() + "' text='" + col.getLabel() + "' sourceType='Custom' />";
				}
			}
			else if (def instanceof RowPerObjectDataSetDefinition) {
				for (DataSetColumn col : ((RowPerObjectDataSetDefinition) def).getDataSetColumns()) {
					id = getNextId(id);
					xml += "<DesignItem type='" + DesignItem.PT_POS + "' id='" + id +"' name='" + col.getLabel() + "' binding='" + col.getName() + "' text='" + col.getLabel() + "' sourceType='Custom' />";
				}
			}
			else if (def instanceof SimpleIndicatorDataSetDefinition) {
				for (SimpleIndicatorColumn col : ((SimpleIndicatorDataSetDefinition) def).getColumns()) {
					id = getNextId(id);
					xml += "<DesignItem type='" + DesignItem.PT_POS + "' id='" + id +"' name='" + col.getLabel() + "' binding='" + col.getName() + "' text='" + col.getLabel() + "' sourceType='Custom' />";
				}
			}
			else if (def instanceof SqlDataSetDefinition) {
				List<String> columns = Context.getService(XReportsService.class).getColumns(((SqlDataSetDefinition) def).getSqlQuery());
				for (String col : columns) {
					id = getNextId(id);
					Element node = map.get(col);
					if (node != null && ((Element)node.getParentNode()).getAttribute("binding").equals(e.getKey())) {
						xml += "<DesignItem type='" + node.getAttribute("type") + "' id='" + node.getAttribute("id") +"' name='" + col + "' binding='" + col + "' text='" + node.getAttribute("text") + "' sourceType='Custom' />";
					}
					else {
						xml += "<DesignItem type='" + DesignItem.PT_POS + "' id='" + id +"' name='" + col + "' binding='" + col + "' text='" + col + "' sourceType='Custom' />";
					}
				}
			}
			
			xml += "</DesignItem>";
		}
		
		xml += "</DesignItems>";
		
		return xml;
		//return " PURCFORMS_FORMDEF_LAYOUT_XML_SEPARATOR " + xml;
	}
	
	private String mergeDesignItems(Document doc, String designItemsXml) throws Exception {

		if (StringUtils.isNotBlank(designItemsXml)) {
			NodeList nodes = doc.getDocumentElement().getElementsByTagName("DesignItems");
			if (nodes != null && nodes.getLength() > 0) {
				Element parent = (Element)nodes.item(0);
				parent.getParentNode().removeChild(parent);
			}
			
			Node node = DOMUtil.fromString2Doc(designItemsXml).getDocumentElement();
			node =  doc.importNode(node, true);
			doc.getDocumentElement().appendChild(node);
		}
		
		return DOMUtil.doc2String(doc);
	}
	
	private Map<String, Element> getItemBindingMap(Document doc) {
		
		HashMap<String, Element> map = new HashMap<String, Element>();
		idlist = new ArrayList<String>();
		
		NodeList nodes = doc.getDocumentElement().getElementsByTagName("DesignItem");
		for (int index = 0; index < nodes.getLength(); index++) {
			Element node = (Element)nodes.item(index);
			map.put(node.getAttribute("binding"), node);
			idlist.add(node.getAttribute("id"));
		}
		
		return map;
	}
	
	private int getNextId(int id) {
		id++;
		while (idlist.contains(id)) {
			id++;
		}
		return id;
	}
}