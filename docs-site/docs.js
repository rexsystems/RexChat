// Highlight active section in sidebar
document.addEventListener('DOMContentLoaded', function() {
    const sections = document.querySelectorAll('.docs-content h1[id], .docs-content h2[id], .docs-content h3[id]');
    const navLinks = document.querySelectorAll('.docs-sidebar a');
    
    function highlightNav() {
        let current = '';
        const scrollPos = window.scrollY + 100;
        
        // If at the top, highlight overview
        if (scrollPos < 200) {
            current = 'overview';
        } else {
            sections.forEach(section => {
                const sectionTop = section.offsetTop;
                
                if (scrollPos >= sectionTop) {
                    current = section.getAttribute('id');
                }
            });
        }
        
        navLinks.forEach(link => {
            link.classList.remove('active');
            if (link.getAttribute('href') === '#' + current) {
                link.classList.add('active');
            }
        });
    }
    
    window.addEventListener('scroll', highlightNav);
    highlightNav();
    
    // Add copy buttons to code blocks
    const codeBlocks = document.querySelectorAll('pre code');
    codeBlocks.forEach(block => {
        const pre = block.parentElement;
        const wrapper = document.createElement('div');
        wrapper.className = 'code-block-wrapper';
        
        const copyBtn = document.createElement('button');
        copyBtn.className = 'copy-btn';
        copyBtn.innerHTML = 'Copy';
        copyBtn.onclick = function() {
            navigator.clipboard.writeText(block.textContent).then(() => {
                copyBtn.innerHTML = 'Copied!';
                setTimeout(() => {
                    copyBtn.innerHTML = 'Copy';
                }, 2000);
            });
        };
        
        pre.parentNode.insertBefore(wrapper, pre);
        wrapper.appendChild(copyBtn);
        wrapper.appendChild(pre);
    });
});
