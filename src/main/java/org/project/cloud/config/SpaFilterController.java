package org.project.cloud.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaFilterController {
    @RequestMapping(value = "{path:^(?!api|index\\.html|assets|config\\.js|favicon\\.ico).*$}/**")
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
