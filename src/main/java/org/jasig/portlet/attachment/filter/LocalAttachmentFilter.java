/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.portlet.attachment.filter;

import java.io.File;
import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jasig.portlet.attachment.model.Attachment;
import org.jasig.portlet.attachment.service.IAttachmentService;
import org.jasig.portlet.attachment.util.DataUtil;
import org.jasig.portlet.attachment.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Chris Waymire (chris@waymire.net)
 */
public final class LocalAttachmentFilter implements Filter {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IAttachmentService attachmentService = null;

    public void init(FilterConfig filterConfig) throws ServletException {}

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String relative = httpServletRequest.getServletPath();
        String path = httpServletRequest.getSession().getServletContext().getRealPath(relative);
        log.debug("Looking up file {}", path);
        File file = new File(path);

        if(!file.exists()) {
            String[] parts = path.split("/");
            int guidIndex = parts.length - 2;
            String guid = parts[guidIndex];

            Attachment attachment = attachmentService.get(guid);
            if (attachment != null) {
                log.debug("Restoring the following  attachment to the server file system:  {}", path);
                byte[] content = DataUtil.decode(attachment.getData());
                FileUtil.write(path, content);

                // CMSPLT-38 First time request for a file is not found after file creation. For some reason,
                // Tomcat's default servlet does not return the file even though it is hydrated onto the file system.
                // Attempted to return a 302 with the same URL hoping Tomcat's default servlet would return the
                // file but the file is still not returned. Unfortunately, we have to duplicate what Tomcat's
                // default servlet will do and return the content.
                file = new File(path);
                HttpServletResponse httpResponse = (HttpServletResponse) response;

                String contentType = httpServletRequest.getSession().getServletContext().getMimeType(path);
                httpResponse.setHeader("Content-Type", contentType);
                httpResponse.setDateHeader("Last-Modified", file.lastModified());
                httpResponse.setHeader("Content-Length", Long.toString(file.length()));
                httpResponse.setStatus(HttpServletResponse.SC_OK);
                httpResponse.getOutputStream().write(content);
                httpResponse.flushBuffer();
                return;
            } else {
                log.info("Attachment not found: {}", path);
            }
        }

        chain.doFilter(request,response);

    }

    public void destroy() {}

}
