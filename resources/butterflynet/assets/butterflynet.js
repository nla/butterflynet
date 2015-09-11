$(document).ready(function() {
    $("abbr.timeago").timeago();


    var tag = null;
    var mounted = false;
    var events = new EventSource("events");
    events.addEventListener("update", function(e) {
        var data = JSON.parse(e.data);
        if (mounted) {
            tag.update({opts: data});
        } else {
            console.log("mounting... " + JSON.stringify(data));
            tag = riot.mount('*', data)[0];
            mounted = true;
        }
    }, false);
});

