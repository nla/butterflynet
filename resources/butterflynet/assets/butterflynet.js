$(document).ready(function() {

    $("abbr.timeago").timeago();

    var csrfToken = $("#csrfTokenField")[0].value;
    var tag = null;
    var mounted = false;
    var events = new EventSource("events");
    events.addEventListener("update", function(e) {
        var data = JSON.parse(e.data);
        data.csrfToken = csrfToken;

        if (mounted) {
            tag.update({opts: data});
        } else if (typeof riot !== 'undefined') {
            console.log("mounting... " + JSON.stringify(data));
            tag = riot.mount('*', data)[0];
            mounted = true;
        }
    }, false);

    var clipboard = new Clipboard(".clipboard-button");
    clipboard.on('success', function(e) {
        console.log("copied");
    });
});

