<capture-list>

    <div each="{capture, i in opts.captures}" class="capture">
        <div class="info">
            <div class="url">
                <a class="archive-url" href="{capture.archiveUrl}">{capture.archiveUrl}</a>
                [<a href="{capture.originalUrl}">original</a>]
            </div>
            <div class="progress" if="{capture.state == 'DOWNLOADING' || capture.state == 'QUEUED'}">
                <div class="progress-bar progress-bar-striped" style="width: {capture.percentage}%"></div>
            </div>
            <div class="detail-line">
                <span class="size" if="{capture.state == 'DOWNLOADING'}"><span>{capture.position}</span> of <span>{capture.length}</span></span>
                <span class="size" if="{capture.state == 'ARCHIVED'}">{capture.length}</span>
                <span class="state" if="{capture.state == 'QUEUED'}">Queued</span>
                &mdash;
                <span class="start-time"><abbr title="{capture.started}" class="timeago">{capture.started}</abbr></span>
            </div>
        </div>
        <div class="actions">
            <button><i class="glyphicon glyphicon-remove" alt="Cancel"></i></button>
        </div>
    </div>

</capture-list>