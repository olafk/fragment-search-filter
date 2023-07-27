package com.liferay.fragment.olafkock.portletfilter;

import com.liferay.fragment.constants.FragmentPortletKeys;
import com.liferay.fragment.contributor.FragmentCollectionContributor;
import com.liferay.fragment.contributor.FragmentCollectionContributorRegistry;
import com.liferay.fragment.model.FragmentCollection;
import com.liferay.fragment.model.FragmentEntry;
import com.liferay.fragment.service.FragmentCollectionLocalService;
import com.liferay.fragment.service.FragmentEntryLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.WebKeys;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
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
 * Assumption: Nobody will ever need more than 640 fragment collections (sic!)
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

	@SuppressWarnings("deprecation")
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
			PrintWriter writer = response.getWriter();
			String interestingText = "id=\"_com_liferay_fragment_web_portlet_FragmentPortlet_fragmentEntries"+key+"PrimaryKeys\"";

			int index = text.lastIndexOf(interestingText);
			if (index >= 0) {
				int formEndIndex = text.indexOf("</form>", index) + "</form>".length();
				writer.write(text.substring(0, formEndIndex));
				writer.write("\n<h2>Also found in:</h2>\n" + more);
				writer.write(text.substring(formEndIndex));
			} else {
				// something in the DOM has changed since this plugin was written.
				// just tail the custom output to the end - it will look weird, but 
				// likely will still work.
				writer.write(text);
				writer.write("\n<h2>Also found in</h2>\n" + more);
			}
		}
	}
	
	private String findMoreEntries(String searchTerm, RenderRequest request, String exclude) {
		StringBuilder result = new StringBuilder();
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
		fragmentCollections.addAll(_fragmentCollectionLocalService.getFragmentCollections(groupId, 0, 640));
		List<FragmentCollection> globalCollection = _fragmentCollectionLocalService.getFragmentCollections(globalId, 0, 640);
		if(globalCollection!=null) {
			fragmentCollections.addAll(globalCollection);
		}
		List<FragmentCollection> otherFragmentCollections = _fragmentCollectionLocalService.getFragmentCollections(0, 640);
		otherFragmentCollections = new LinkedList<FragmentCollection>(otherFragmentCollections);
		for (Iterator<FragmentCollection> iterator = otherFragmentCollections.iterator(); iterator.hasNext();) {
			FragmentCollection fc = iterator.next();
			if(fc.getCompanyId() != themeDisplay.getCompanyId() || fc.getGroupId() == groupId || fc.getGroupId() == globalId) {
				iterator.remove();
			}
		}
		
		searchTerm = searchTerm.toLowerCase();
		List<FragmentCollectionContributor> contributors = _fragmentCollectionContributorRegistry.getFragmentCollectionContributors();
		
		String part = fragmentCollectionToLI(searchTerm, exclude, themeDisplay, groupId, globalId, uri, queryString, fragmentCollections);
		if(part.length()>8) {
			result.append("<h3>Fragment Collections <i>in this site or global</i></h3><ul>")
			.append(part)
			.append("</ul>");
		}
		
		part = contributorsToLI(searchTerm, exclude, uri, queryString, contributors);
		if(part.length()>8) {
			result.append("<h3>Fragment Collections Contributors</h3><p><i>(note: not editable)</i></p><ul>")
			.append(part)
			.append("</ul>");
		}

		part = fragmentCollectionToLI(searchTerm, exclude, themeDisplay, groupId, globalId, uri, queryString, otherFragmentCollections);
		if(part.length()>8) {
			result.append("<div style=\"opacity:50%;\"><h3>Fragment Collections <i>in other sites</i></h3>")
			.append("<p>Note: When following any of those links, you'll show the other site's fragments in the context of this site. Don't be confused!</p>")
			.append("<ul>")
			.append(part)
			.append("</ul></div>");
		}
		
		return result.toString();
	}

	/***
	 * Convert FragmentContributors to a <li></li> String, linking only the fragment contributor's name
	 * @param searchTerm
	 * @param exclude
	 * @param uri
	 * @param queryString
	 * @param contributors
	 * @return
	 */
	
	private String contributorsToLI(String searchTerm, String exclude, String uri,
			String queryString, List<FragmentCollectionContributor> contributors) {
		StringBuilder result = new StringBuilder();
		for (FragmentCollectionContributor contributor : contributors) {
			if(contributor.getFragmentCollectionKey().equals(exclude)) {
				continue;
			}
			List<FragmentEntry> fragmentEntries = contributor.getFragmentEntries();
			for (FragmentEntry fragmentEntry : fragmentEntries) {
				if(fragmentEntry.getName().toLowerCase().indexOf(searchTerm)>=0) {
					result.append("<li><i>")
					.append(fragmentEntry.getName())
					.append("</i> from ")
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
		return result.toString();
	}

	/***
	 * Convert FragmentCollections to a <li></li> String, while linking
	 * the fragment and the fragmentCollection to proper UI URLs
	 * 
	 * If this collection is not in the current or global site, also 
	 * include the site name
	 * 
	 * @param searchTerm
	 * @param exclude
	 * @param themeDisplay
	 * @param groupId
	 * @param globalId
	 * @param uri
	 * @param queryString
	 * @param fragmentCollections
	 * @return
	 */
	
	private String fragmentCollectionToLI(String searchTerm, String exclude,
			ThemeDisplay themeDisplay, long groupId, long globalId, String uri, String queryString,
			List<FragmentCollection> fragmentCollections) {
		StringBuilder result = new StringBuilder();
		for (FragmentCollection collection : fragmentCollections) {
			if((""+collection.getPrimaryKey()).equals(exclude)) {
				continue;
			}
			List<FragmentEntry> fragmentEntries = fragmentEntryLocalService.getFragmentEntries(collection.getPrimaryKey());
			for (FragmentEntry fragmentEntry : fragmentEntries) {
				if(fragmentEntry.getName().toLowerCase().indexOf(searchTerm)>=0) {
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
					.append("</a>");
					if(fragmentEntry.getGroupId() != groupId && fragmentEntry.getGroupId() != globalId ) {
						Group group;
						try {
							group = _groupLocalService.getGroup(fragmentEntry.getGroupId());
							if(group != null) {
								result.append(" <i>in ")
								.append(group.getName(themeDisplay.getLocale()))
								.append("</i>");
							} else {
								result.append(" <i>somewhere</i>");
							}
						} catch (PortalException e) {
							result.append("... but: ")
							.append(e.getClass().getName())
							.append(" ")
							.append(e.getMessage());
						}
					}

					result.append("</li>");
				}
			}
		}
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

	@Reference
	private GroupLocalService _groupLocalService;
}