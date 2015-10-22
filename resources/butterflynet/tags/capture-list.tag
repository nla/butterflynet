<clipboard-button>
    <button class="clipboard-button" data-clipboard-text="{opts.text}"><i class="glyphicon glyphicon-copy"></i> Copy Link</button>
</clipboard-button>

<timeago>
    <abbr title="{opts.time}" class="timeago">{opts.time}</abbr>

    <script>
        if (typeof window !== "undefined") {
            this.on('mount', function () {
                $(this.root).find("abbr").timeago();
            });
        }
    </script>
</timeago>

<capture-list>

    <div each="{capture, i in opts.captures}" class="capture">
        <div class="details">
            <div class="info">
                <div class="progress" if="{capture.state == 'DOWNLOADING' || capture.state == 'QUEUED'}">
                    <div class="progress-bar progress-bar-striped" style="width: {capture.percentage}%"></div>
                </div>
                <div class="detail-line">
                    <div>
                        <span class="size" if="{capture.state == 'DOWNLOADING'}"><span>{capture.position}</span> of <span>{capture.length}</span></span>
                        <span class="size" if="{capture.state == 'ARCHIVED'}">{capture.length}</span>
                        <span class="state" if="{capture.state == 'QUEUED'}">Queued</span>
                        <span class="failure-reason" if="{capture.state == 'FAILED'}">{capture.reason}</span>
                        &mdash;
                        <span class="start-time"><timeago time="{capture.started}"></timeago></span>
                    </div>
                </div>
            </div>
            <div class="actions">
                <form action="cancel" method="post" if="{capture.state == 'DOWNLOADING' || capture.state == 'QUEUED'}">
                    <input type="hidden" name="id" value="{capture.id}">
                    <input type="hidden" name="csrfToken" value="{parent.csrfToken}">
                    <button><i class="glyphicon glyphicon-remove" alt="Cancel"></i> Cancel</button>
                </form>
            </div>
        </div>
        <div class="url">
            <div class="url-box original-url">
                <span>Original:</span>
                <a href="{capture.originalUrl}">{capture.originalUrl}</a>
                <button class="clipboard-button" data-clipboard-text="{capture.originalUrl}">
                    <i class="glyphicon glyphicon-copy"></i> Copy Original Link
                </button>
            </div>
            <div class="url-box archive-url" if="{capture.state != 'FAILED'}">
                <span>Archive:</span>
                <a href="{capture.archiveUrl}">{capture.archiveUrl}</a>
                <button class="clipboard-button" data-clipboard-text="{capture.originalUrl}">
                    <i class="glyphicon glyphicon-copy"></i> Copy Archive Link
                </button>
            </div>
        </div>
    </div>

    <script>
        this.csrfToken = opts.csrfToken;
    </script>

</capture-list>