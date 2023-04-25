package com.liferay.fragment.olafkock.portletfilter;

import com.liferay.fragment.constants.FragmentPortletKeys;
import com.liferay.fragment.contributor.FragmentCollectionContributor;
import com.liferay.fragment.contributor.FragmentCollectionContributorRegistry;
import com.liferay.fragment.model.FragmentCollection;
import com.liferay.fragment.model.FragmentEntry;
import com.liferay.fragment.service.FragmentCollectionLocalService;
import com.liferay.fragment.service.FragmentEntryLocalService;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.WebKeys;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.filter.FilterChain;
import javax.portlet.filter.FilterConfig;
import javax.portlet.filter.PortletFilter;
import javax.portlet.filter.RenderFilter;
import javax.portlet.filter.RenderResponseWrapper;
import javax.servlet.http.HttpServletRequest;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Show filter results for Fragments that match a search term but are in 
 * different fragment collections than currently displayed.
 * 
 * Very crude UI
 * 
 * @author Olaf Kock
 */
@Component(
		immediate=true,
		property = {
				"javax.portlet.name=" + FragmentPortletKeys.FRAGMENT
		},
		service=PortletFilter.class)
public class FragmentSearchPortletFilter implements RenderFilter {

	@Override
	public void init(FilterConfig filterConfig) throws PortletException {
	}

	@Override
	public void destroy() {
	}

	@Override
	public void doFilter(RenderRequest request, RenderResponse response, FilterChain chain)
			throws IOException, PortletException {
		String searchTerm = request.getParameter("keywords");
		if(searchTerm == null) {
			chain.doFilter(request, response);
			return;
		}
		String collectionId  = request.getParameter("fragmentCollectionId");
		String collectionKey = request.getParameter("fragmentCollectionKey");
		String key = collectionId==null?collectionKey:collectionId;
		String more = findMoreEntries(searchTerm, request, key);
		if(more.length()<=10) {
			chain.doFilter(request, response);
			return;
		}
		
		RenderResponseWrapper renderResponseWrapper = new BufferedRenderResponseWrapper(response);
		chain.doFilter(request, renderResponseWrapper);

		String text = renderResponseWrapper.toString();
		
		if (text != null) {
			String interestingText = "id=\"_com_liferay_fragment_web_portlet_FragmentPortlet_fragmentEntries"+key+"PrimaryKeys\"";

			int index = text.lastIndexOf(interestingText);
			if (index >= 0) {
				int formEndIndex = text.indexOf("</form>", index) + "</form>".length();
				String newText1 = text.substring(0, formEndIndex);
				String newText2 = "\n<h2>Also found in</h2>\n" + more;
				String newText3 = text.substring(formEndIndex);
				
				String newText = newText1 + newText2 + newText3;
				
				response.getWriter().write(newText);
			}
		}
	}
	
	private String findMoreEntries(String searchTerm, RenderRequest request, String exclude) {
		StringBuilder result = new StringBuilder("<ul>");
		ThemeDisplay themeDisplay = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);
		long groupId = themeDisplay.getScopeGroupId();
		long globalId = themeDisplay.getCompanyGroupId();
		HttpServletRequest originalServletRequest = PortalUtil.getOriginalServletRequest(themeDisplay.getRequest());
		String uri = originalServletRequest.getRequestURI();
		if(uri.indexOf("/-/")>0) {
			uri = uri.substring(0, uri.indexOf("/-/")+2);
		} else if(uri.indexOf("/~/")>0) {
			uri = uri.substring(0, uri.indexOf("/~/")+2);
		} else {
			return "";
		}
		String queryString = "?_" + FragmentPortletKeys.FRAGMENT + "_keywords="
							+ HtmlUtil.escape(searchTerm); // works without p_p_auth

		
		List<FragmentCollection> fragmentCollections = new LinkedList<FragmentCollection>(); 
		fragmentCollections.addAll(_fragmentCollectionLocalService.getFragmentCollections(groupId, 0, 100));
		List<FragmentCollection> globalCollection = _fragmentCollectionLocalService.getFragmentCollections(globalId, 0, 100);
		if(globalCollection!=null) {
			fragmentCollections.addAll(globalCollection);
		}

		List<FragmentCollectionContributor> contributors = _fragmentCollectionContributorRegistry.getFragmentCollectionContributors();

		for (FragmentCollection collection : fragmentCollections) {
			if((""+collection.getPrimaryKey()).equals(exclude)) {
				continue;
			}
			List<FragmentEntry> fragmentEntries = fragmentEntryLocalService.getFragmentEntries(collection.getPrimaryKey());
			for (FragmentEntry fragmentEntry : fragmentEntries) {
				if(fragmentEntry.getName().toLowerCase().indexOf(searchTerm.toLowerCase())>=0) {
					result.append("<li>")
					.append("<a href=\"")
					.append(uri)
					.append("/fragments/fragment_collection/")
					.append(collection.getPrimaryKey())
					.append("/fragment_entry/")
					.append(fragmentEntry.getPrimaryKey())
					.append("/edit")
					.append("\">")
					.append(fragmentEntry.getName())
					.append("</a> from ")
					.append("<a href=\"")
					.append(uri)
					.append("/fragments/fragment_collection/")
					.append(collection.getPrimaryKey())
					.append(queryString)
					.append("\">")
					.append(collection.getName())
					.append("</a></li>");
				}
			}
		}
		for (FragmentCollectionContributor contributor : contributors) {
			if(contributor.getFragmentCollectionKey().equals(exclude)) {
				continue;
			}
			List<FragmentEntry> fragmentEntries = contributor.getFragmentEntries();
			for (FragmentEntry fragmentEntry : fragmentEntries) {
				if(fragmentEntry.getName().indexOf(searchTerm)>=0) {
					result.append("<li>")
					.append(fragmentEntry.getName())
					.append(" from ")
					.append("<a href=\"")
					.append(uri)
					.append("/fragments/fragment_collection/")
					.append(contributor.getFragmentCollectionKey())
					.append(queryString)
					.append("\">")
					.append(contributor.getName())
					.append("</a></li>");
				}
			}
		}
		result.append("</ul>");
		return result.toString();
	}
	
	@Reference
	private FragmentEntryLocalService fragmentEntryLocalService;
	
	@Reference
	private FragmentCollectionContributorRegistry
		_fragmentCollectionContributorRegistry;
	
	@Reference
	private FragmentCollectionLocalService 
		_fragmentCollectionLocalService;
}