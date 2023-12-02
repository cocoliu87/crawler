import React from "react";
import { Card, CardContent, Typography, Link } from "@mui/material";

export default function Item({ id, url, text }) {
  return (
    <div>
      <Card>
        {id}
        <CardContent>
          <Typography variant="h5" component="div">
            <Link href={`${url}`} target="_blank">
              Click to view the detail
            </Link>
          </Typography>
          <Typography variant="body2">{text}</Typography>
        </CardContent>
      </Card>
    </div>
  );
}
